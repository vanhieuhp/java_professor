package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.MatchField;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Xử lý MỘT bản ghi trong hàng đợi — bước B3.
 *
 * <h2>Ranh giới transaction</h2>
 * Bean riêng, không phải một method của vòng lặp, vì hai lý do:
 * <ul>
 *   <li>Spring proxy {@code @Transactional} theo lời gọi từ ngoài vào bean. Gọi
 *       {@code this.process(...)} từ trong cùng một class thì annotation bị bỏ qua hoàn toàn —
 *       lỗi im lặng, mọi thứ vẫn "chạy" nhưng không có transaction nào cả.</li>
 *   <li>Vòng lặp B3/B5 <b>không được</b> nằm trong transaction: 5 triệu khách hàng trong một
 *       transaction là một transaction sống nhiều giờ, giữ khóa và làm phình WAL. Mỗi khách
 *       hàng là một transaction ngắn, độc lập.</li>
 * </ul>
 * Trong transaction này: cập nhật trạng thái 'Đã xử lý' + sinh bản ghi kết quả rủi ro.
 * Hai việc đó phải cùng thành công hoặc cùng thất bại — nếu tách ra, một cú chết đúng giữa
 * hai lệnh sẽ tạo ra khách hàng "đã xử lý" mà không có kết quả, và không lần quét nào sau
 * này nhặt lại được.
 * <p>
 * Lời gọi Core ví <b>không</b> nằm ở đây — kết quả được ghi với trạng thái
 * {@code PENDING} và một job riêng gửi đi (Phase 5).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanRecordProcessor {

    private final CustomerScanEventRepository scanQueueRepository;
    private final CustomerRiskResultRepository riskResultRepository;
    private final RiskEvaluator riskEvaluator;

    /**
     * @param blacklistOnly true = TH1/TH2 (chỉ DS đen); false = TH3 (toàn bộ tiêu chí trừ DS đen)
     * @return kết quả rủi ro nếu trùng, rỗng nếu không
     */
    @Transactional
    public Optional<RiskAssessment> process(CustomerScanEvent queued, WatchlistSnapshot lists,
                                            boolean blacklistOnly) {
        CustomerSnapshot snapshot = ScanEventMapper.toSnapshot(queued);

        Optional<RiskAssessment> assessment = blacklistOnly
                ? riskEvaluator.evaluateAgainstBlacklist(snapshot, lists)
                : riskEvaluator.evaluateAgainstWatchlists(snapshot, lists);

        // Cập nhật 'Đã xử lý' cho CẢ hai nhánh — spec A.3-B3 và A.5-B3 đều nói rõ:
        // không trùng vẫn phải đánh dấu đã xử lý, nếu không vòng lặp B5 sẽ lấy lại mãi mãi.
        queued.setStatus(ScanStatus.DA_XU_LY);
        queued.setProcessedAt(Instant.now());
        scanQueueRepository.save(queued);

        assessment.ifPresent(a -> writeResult(queued, a));
        return assessment;
    }

    private void writeResult(CustomerScanEvent queued, RiskAssessment result) {
        // Hạ cờ bản ghi cũ TRƯỚC khi chèn bản mới. Cùng transaction — nếu tách ra,
        // unique index uq_result_latest_per_cif sẽ từ chối và lộ ngay lỗi.
        riskResultRepository.clearLatestFlag(queued.getCif());
        riskResultRepository.flush();

        riskResultRepository.save(CustomerRiskResult.builder()
                .cif(queued.getCif())
                .scanBatchId(queued.getScanBatchId())
                .scanQueueId(queued.getId())
                .triggerType(queued.getTriggerType())
                .riskLevel(result.getRiskLevel())
                .riskScore(result.getRiskScore())
                .reason(result.getReason())
                .categoryCode(result.getCategoryCode())
                .entryId(result.getEntryId())
                .matchedFields(joinFields(result))
                .lockCifRequired(result.isLockCifRequired())
                .latest(true)
                .build());

        log.warn("CIF {} TRÙNG [{}] điểm {} — bản ghi #{} qua {}{}",
                result.getCif(), result.getCategoryCode(), result.getRiskScore(),
                result.getEntryId(), result.getMatchedFields(),
                result.isLockCifRequired() ? " — YÊU CẦU KHÓA CIF" : "");
    }

    private static String joinFields(RiskAssessment result) {
        if (result.getMatchedFields() == null) {
            return null;
        }
        return result.getMatchedFields().stream().map(MatchField::name).collect(Collectors.joining(","));
    }
}
