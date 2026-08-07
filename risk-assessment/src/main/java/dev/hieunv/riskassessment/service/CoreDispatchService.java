package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.client.CorePermanentException;
import dev.hieunv.riskassessment.client.CoreTransientException;
import dev.hieunv.riskassessment.client.CoreWalletClient;
import dev.hieunv.riskassessment.client.SimpleCircuitBreaker;
import dev.hieunv.riskassessment.constant.CoreSendStatus;
import dev.hieunv.riskassessment.dto.CoreDispatchStats;
import dev.hieunv.riskassessment.dto.CoreRiskUpdateAck;
import dev.hieunv.riskassessment.dto.CoreRiskUpdateRequest;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * Gửi kết quả rủi ro sang Core ví — spec A.3-B4, A.4-B3/B4, A.5-B4.
 *
 * <h2>Vì sao là job, không phải gọi thẳng lúc chấm điểm</h2>
 * Nếu {@link ScanRecordProcessor} gọi Core ngay trong transaction của nó:
 * <ul>
 *   <li>một lần Core treo sẽ giữ transaction DB suốt thời gian timeout;</li>
 *   <li>nếu transaction rollback sau khi Core đã nhận, hai bên lệch nhau vĩnh viễn — Core
 *       đã khóa ví mà PCRT không còn bản ghi nào giải thích tại sao;</li>
 *   <li>Core down đồng nghĩa batch không chạy được, dù việc chấm điểm chẳng liên quan gì.</li>
 * </ul>
 * Tách ra: chấm điểm ghi kết quả trạng thái PENDING và kết thúc. Job này gửi đi. Core down
 * chỉ làm hàng đợi dài ra, không làm hỏng gì cả.
 *
 * <h2>Ba lớp phòng vệ, mỗi lớp cho một loại hỏng khác nhau</h2>
 * <ol>
 *   <li><b>Retry</b> (trong {@link CoreWalletClient}) — mili-giây, cho gói tin rớt.</li>
 *   <li><b>Cầu dao</b> — khi Core down thật, ngừng gọi để không tự làm khổ mình và Core.</li>
 *   <li><b>Backoff + trạng thái trong DB</b> — phút tới giờ, cho sự cố dài. Sống sót qua cả
 *       việc PCRT restart, vì trạng thái nằm ở DB chứ không trong bộ nhớ.</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreDispatchService {

    private final CustomerRiskResultRepository riskResultRepository;
    private final CoreDispatchWriter dispatchWriter;
    private final CoreWalletClient coreClient;
    private final PcrtConfigService configService;

    private SimpleCircuitBreaker circuitBreaker;

    @PostConstruct
    public void initCircuitBreaker() {
        circuitBreaker = new SimpleCircuitBreaker(
                "core-wallet",
                configService.getInt("core.circuit-breaker.failure-threshold", 5),
                Duration.ofSeconds(configService.getInt("core.circuit-breaker.open-seconds", 30)));
    }

    /** Một lượt gửi. Được gọi theo chu kỳ đọc từ {@code pcrt_config}, hoặc gọi tay qua API. */
    public CoreDispatchStats dispatchDue() {
        int maxAttempts = configService.getInt("core.dispatch.max-attempts", 5);

        // Công tắc tạm dừng. Cần cho cửa sổ bảo trì bên Core: thay vì để job đập vào một
        // hệ thống đang nâng cấp rồi đếm đủ số lần thử và chuyển hết sang FAILED, chỉ cần
        // ngừng gửi — kết quả nằm yên ở PENDING và tự đi tiếp khi bật lại.
        if (!configService.getBoolean("core.dispatch.enabled", true)) {
            return stats(0, 0, 0, maxAttempts);
        }

        int batchSize = configService.getInt("core.dispatch.batch-size", 200);
        int backoffBase = configService.getInt("core.dispatch.backoff-base-seconds", 10);

        int sent = 0;
        int failed = 0;
        int skipped = 0;

        List<CustomerRiskResult> due = riskResultRepository.findDueForDispatch(maxAttempts, batchSize);
        for (CustomerRiskResult result : due) {
            if (!circuitBreaker.allowRequest()) {
                // Cầu dao đang mở: bỏ qua phần còn lại của lô. Các bản ghi này vẫn ở
                // PENDING/FAILED nên lượt chạy sau sẽ lấy lại — không mất gì.
                skipped = due.size() - sent - failed;
                log.warn("Cầu dao đang MỞ — bỏ qua {} kết quả trong lượt này", skipped);
                break;
            }
            if (send(result, backoffBase)) {
                sent++;
            } else {
                failed++;
            }
        }

        if (sent > 0 || failed > 0) {
            log.info("Gửi Core: thành công {}, thất bại {}, bỏ qua {} — cầu dao {}",
                    sent, failed, skipped, circuitBreaker.state());
        }
        return stats(sent, failed, skipped, maxAttempts);
    }

    private boolean send(CustomerRiskResult result, int backoffBase) {
        CoreRiskUpdateRequest request = CoreRiskUpdateRequest.builder()
                // Khóa chống trùng bám vào id của bản ghi kết quả — ổn định qua mọi lần gửi
                // lại. Nếu sinh UUID mới mỗi lần thử thì idempotency mất tác dụng hoàn toàn.
                .idempotencyKey("RESULT-" + result.getId())
                .cif(result.getCif())
                .riskLevel(result.getRiskLevel())
                .riskScore(result.getRiskScore())
                .reason(result.getReason())
                // Q1 — chỉ TH1/TH2 mới kéo theo khóa CIF. TH3 chỉ cập nhật điểm.
                .lockCif(result.isLockCifRequired())
                .build();

        try {
            CoreRiskUpdateAck ack = coreClient.sendRiskAssessment(request);
            circuitBreaker.recordSuccess();
            dispatchWriter.markSent(result.getId());

            if (ack != null && ack.isDuplicate()) {
                // Đây chính là bằng chứng idempotency hoạt động: PCRT tưởng chưa gửi được,
                // nhưng Core đã nhận từ lần trước và không xử lý lại.
                log.info("CIF {} — Core báo lệnh trùng, không xử lý lại (khóa {})",
                        result.getCif(), request.getIdempotencyKey());
            }
            return true;

        } catch (CorePermanentException e) {
            log.error("CIF {} — Core từ chối vĩnh viễn, ngừng gửi lại: {}", result.getCif(), e.getMessage());
            dispatchWriter.markFailed(result.getId(), e.getMessage(), backoffBase, false);
            return false;

        } catch (CoreTransientException e) {
            circuitBreaker.recordFailure();
            log.warn("CIF {} — gửi Core thất bại (lần {}): {}",
                    result.getCif(), result.getAttemptCount() + 1, e.getMessage());
            dispatchWriter.markFailed(result.getId(), e.getMessage(), backoffBase, true);
            return false;
        }
    }

    /** Đưa các kết quả FAILED trở lại hàng đợi sau khi đã sửa nguyên nhân gốc. */
    public int requeueFailed() {
        int n = dispatchWriter.requeueFailed();
        log.warn("Đã đưa {} kết quả FAILED trở lại hàng đợi gửi Core", n);
        return n;
    }

    public CoreDispatchStats currentStats() {
        return stats(0, 0, 0, configService.getInt("core.dispatch.max-attempts", 5));
    }

    private CoreDispatchStats stats(int sent, int failed, int skipped, int maxAttempts) {
        return CoreDispatchStats.builder()
                .sentThisRun(sent)
                .failedThisRun(failed)
                .skippedThisRun(skipped)
                .pending(riskResultRepository.countByCoreSendStatus(CoreSendStatus.PENDING))
                .failed(riskResultRepository.countByCoreSendStatus(CoreSendStatus.FAILED))
                .sent(riskResultRepository.countByCoreSendStatus(CoreSendStatus.SENT))
                .exhausted(riskResultRepository.countExhausted(maxAttempts))
                .circuitState(circuitBreaker.state().name())
                .consecutiveFailures(circuitBreaker.consecutiveFailures())
                .build();
    }
}
