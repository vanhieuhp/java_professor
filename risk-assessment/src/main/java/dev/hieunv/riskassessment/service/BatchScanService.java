package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.entity.ScanBatch;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import dev.hieunv.riskassessment.repository.ScanBatchRepository;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Điều phối batch — B1 → B5 của cả TH1 (A.3) và TH3 (A.5).
 * <p>
 * Hai flow dùng chung toàn bộ đường ống. Khác nhau đúng hai điểm, và cả hai đều là tham số
 * chứ không phải nhánh code:
 * <ul>
 *   <li><b>Tập khách hàng đọc từ Core</b> — TH1/TH3a lấy tất cả, TH3b lọc theo ngày T-1.</li>
 *   <li><b>Danh sách đem ra so khớp</b> — TH1 chỉ DS đen, TH3 mọi thứ trừ DS đen.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchScanService {

    private final ScanBatchRepository scanBatchRepository;
    private final CustomerScanEventRepository scanQueueRepository;
    private final CustomerRiskResultRepository riskResultRepository;
    private final WatchlistCategoryRepository categoryRepository;
    private final ScanEnqueueService enqueueService;
    private final ScanRecordProcessor recordProcessor;
    private final WatchlistServiceImpl blackListServiceImpl;
    private final PcrtConfigService configService;
    private final TaskExecutor pcrtBatchExecutor;

    // =================================================================
    // TH1 — DS đen được điều chỉnh (A.3)
    // =================================================================

    /**
     * Kích hoạt bằng SỰ KIỆN (DS đen thay đổi), không phải cron. Spec A.1-T1 nói rõ
     * "realtime": chặn một người trong danh sách trừng phạt mà đợi tới 2 giờ sáng hôm sau
     * thì cả ngày hôm đó họ vẫn giao dịch được.
     */
    public UUID startBlacklistScan() {
        return start(TriggerType.T1, "DS đen được điều chỉnh");
    }

    // =================================================================
    // TH3 — đánh giá định kỳ (A.5)
    // =================================================================

    /**
     * Bước B1 của TH3: kiểm tra DS mẫu (khác DS đen) có được điều chỉnh trong ngày liền
     * trước không, rồi chọn nhánh.
     * <ul>
     *   <li><b>Có</b> → T3A: danh sách đã đổi nên mọi khách hàng đều phải chấm lại.</li>
     *   <li><b>Không</b> → T3B: danh sách y nguyên, chỉ khách hàng vừa thay đổi mới có thể
     *       ra kết quả khác — quét lại toàn bộ là lãng phí.</li>
     * </ul>
     */
    public UUID startPeriodicScan() {
        ZoneId zone = configService.zone();
        LocalDate yesterday = LocalDate.now(zone).minusDays(1);
        Instant from = yesterday.atStartOfDay(zone).toInstant();
        Instant to = yesterday.plusDays(1).atStartOfDay(zone).toInstant();

        boolean listsChanged = categoryRepository
                .existsByBlacklistFalseAndActiveTrueAndEntriesChangedAtBetween(from, to);

        if (listsChanged) {
            log.info("TH3 — DS mẫu có điều chỉnh ngày {}, quét TOÀN BỘ khách hàng (T3A)", yesterday);
            return start(TriggerType.T3A, "DS mẫu điều chỉnh ngày " + yesterday);
        }
        log.info("TH3 — DS mẫu không đổi, chỉ quét khách hàng phát sinh ngày {} và điểm khác 7 (T3B)", yesterday);
        return start(TriggerType.T3B, "KH phát sinh ngày " + yesterday);
    }

    // =================================================================

    /** Tạo bản ghi batch rồi chạy nền — caller (HTTP hoặc cron) không bị chặn. */
    private UUID start(TriggerType triggerType, String note) {
        UUID batchId = UUID.randomUUID();
        scanBatchRepository.save(ScanBatch.builder()
                .id(batchId)
                .triggerType(triggerType)
                .status(BatchStatus.ENQUEUING)
                .note(note)
                .build());

        runBatchAsync(batchId);
        return batchId;
    }

    /** Chạy B3→B5 của một batch đã có trên thread nền. Dùng cho batch do nơi khác nạp (T1R). */
    public void runBatchAsync(UUID batchId) {
        pcrtBatchExecutor.execute(() -> runBatch(batchId));
    }

    /** B1 → B5. Chạy trên thread nền. */
    public void runBatch(UUID batchId) {
        ScanBatch batch = scanBatchRepository.findById(batchId).orElseThrow();
        int pageSize = configService.pageSize();

        try {
            if (batch.getStatus() == BatchStatus.ENQUEUING) {
                int enqueued = enqueue(batch, pageSize);
                batch.setEnqueuedCount(enqueued);
                batch.setStatus(BatchStatus.PROCESSING);
                scanBatchRepository.save(batch);
            }
            process(batch, pageSize);

            batch.setStatus(BatchStatus.COMPLETED);
            batch.setFinishedAt(Instant.now());
            scanBatchRepository.save(batch);
            log.info("Batch {} HOÀN THÀNH — nạp {}, xử lý {}, trùng {}",
                    batchId, batch.getEnqueuedCount(), batch.getProcessedCount(), batch.getMatchedCount());

        } catch (RuntimeException e) {
            log.error("Batch {} THẤT BẠI: {}", batchId, e.getMessage(), e);
            batch.setStatus(BatchStatus.FAILED);
            batch.setFinishedAt(Instant.now());
            batch.setNote(truncate(e.getMessage()));
            scanBatchRepository.save(batch);
        }
    }

    private int enqueue(ScanBatch batch, int pageSize) {
        if (batch.getTriggerType() == TriggerType.T1R) {
            // T1R nạp hàng đợi ở ReverseScanService rồi mới giao batch sang đây ở trạng thái
            // PROCESSING, nên đường này chỉ chạy khi service chết đúng lúc đang nạp. Việc nạp
            // ngược tính bằng mili-giây — bỏ đi và kích hoạt lại rẻ hơn nhiều so với dựng cơ
            // chế resume cho nó. Không phải thao tác nào cũng đáng được resume.
            throw new IllegalStateException(
                    "Batch T1R chết khi đang nạp hàng đợi — kích hoạt lại lệnh quét ngược");
        }
        if (batch.getTriggerType() == TriggerType.T3B) {
            ZoneId zone = configService.zone();
            LocalDate yesterday = LocalDate.now(zone).minusDays(1);
            return enqueueService.enqueueChangedYesterday(
                    batch.getId(), TriggerType.T3B, pageSize,
                    yesterday.atStartOfDay(zone).toInstant(),
                    yesterday.plusDays(1).atStartOfDay(zone).toInstant());
        }
        return enqueueService.enqueueAllActive(batch.getId(), batch.getTriggerType(), pageSize);
    }

    /**
     * B3 + B5 — vòng lặp lấy việc, xử lý, kiểm tra còn việc không.
     * <p>
     * Vòng lặp này KHÔNG có {@code @Transactional}: mỗi bản ghi là một transaction riêng
     * bên trong {@link ScanRecordProcessor}. Nhờ vậy tiến độ được commit dần, và khi job
     * chết thì chạy lại chỉ nhặt các bản ghi còn 'Chờ xử lý' — chính là cơ chế resume mà
     * Q9 hỏi tới.
     * <p>
     * Ảnh chụp danh sách lấy MỘT LẦN ở đây, không lấy lại trong vòng lặp: cả lần quét phải
     * dùng cùng một phiên bản dữ liệu thì kết quả mới giải trình được.
     */
    private void process(ScanBatch batch, int pageSize) {
        // freshSnapshot chứ không phải snapshot: lần quét này khởi động VÌ danh sách vừa đổi,
        // nên nó bắt buộc phải so khớp với phiên bản danh sách đã có thay đổi đó.
        WatchlistSnapshot lists = blackListServiceImpl.freshSnapshot();
        boolean blacklistOnly = batch.getTriggerType().isBlacklistOnly();

        // Đồng bộ bộ đếm với sự thật trong DB trước khi chạy.
        // Bộ đếm chỉ được lưu sau mỗi trang, nên khi job chết giữa trang nó bị lạc hậu so
        // với số bản ghi đã thực sự commit. Nguồn chân lý là bảng hàng đợi, không phải biến đếm.
        batch.setProcessedCount(
                (int) scanQueueRepository.countByScanBatchIdAndStatus(batch.getId(), ScanStatus.DA_XU_LY));
        batch.setMatchedCount((int) riskResultRepository.countByScanBatchId(batch.getId()));

        while (true) {
            List<CustomerScanEvent> page = scanQueueRepository.findPendingBatch(batch.getId(), pageSize);
            if (page.isEmpty()) {
                break;  // B5: không còn khách hàng nào chờ xử lý → kết thúc
            }

            int processedInPage = 0;
            for (CustomerScanEvent queued : page) {
                try {
                    if (recordProcessor.process(queued, lists, blacklistOnly).isPresent()) {
                        batch.setMatchedCount(batch.getMatchedCount() + 1);
                    }
                    batch.setProcessedCount(batch.getProcessedCount() + 1);
                    processedInPage++;
                } catch (RuntimeException e) {
                    log.error("Batch {} — lỗi khi xử lý CIF {}: {}",
                            batch.getId(), queued.getCif(), e.getMessage(), e);
                }
            }

            // Không bản ghi nào đi qua được: bản ghi vẫn ở 'Chờ xử lý' nên vòng lặp sẽ lấy
            // lại đúng trang đó mãi mãi. Dừng lại và báo lỗi thay vì quay vô hạn.
            if (processedInPage == 0) {
                throw new IllegalStateException(
                        "Không xử lý được bản ghi nào trong trang, dừng để tránh vòng lặp vô hạn");
            }
            scanBatchRepository.save(batch);
        }
    }

    // =================================================================
    // Q9 — resume
    // =================================================================

    /**
     * Tiếp tục các lần quét còn dở sau khi service khởi động lại.
     * <p>
     * An toàn vì hai thứ: trạng thái nằm trong DB chứ không trong bộ nhớ tiến trình, và
     * hàng đợi đã snapshot đủ dữ liệu KH nên không cần đọc lại Core. Batch đang ở
     * ENQUEUING sẽ nạp tiếp — unique index {@code uq_queue_batch_cif} chặn việc sinh trùng.
     */
    public List<UUID> resumeUnfinished() {
        List<ScanBatch> unfinished = scanBatchRepository.findByStatusInOrderByStartedAtAsc(
                List.of(BatchStatus.ENQUEUING, BatchStatus.PROCESSING));

        for (ScanBatch batch : unfinished) {
            long remaining = scanQueueRepository.countByScanBatchIdAndStatus(batch.getId(), ScanStatus.CXL);
            log.info("Tiếp tục batch {} ({}), còn {} khách hàng chờ xử lý",
                    batch.getId(), batch.getTriggerType(), remaining);
            pcrtBatchExecutor.execute(() -> runBatch(batch.getId()));
        }
        return unfinished.stream().map(ScanBatch::getId).toList();
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
