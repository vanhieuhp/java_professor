package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.entity.ScanBatch;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import dev.hieunv.riskassessment.repository.ScanBatchRepository;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.service.BatchScanService;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import dev.hieunv.riskassessment.service.ScanRecordProcessor;
import dev.hieunv.riskassessment.service.WatchlistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BatchScanServiceImpl implements BatchScanService {

    private final ScanBatchRepository scanBatchRepository;
    private final CustomerScanEventRepository customerScanEventRepo;
    private final CustomerRiskResultRepository riskResultRepository;
    private final WatchlistCategoryRepository categoryRepository;
    private final ScanEnqueueServiceImpl enqueueService;
    private final ScanRecordProcessor recordProcessor;
    private final WatchlistService blackListService;
    private final PcrtConfigService configService;
    private final TaskExecutor pcrtBatchExecutor;

    // T1 — blacklist changed
    @Override
    public UUID startBlacklistScan() {
        return start(TriggerType.T1, "DS đen được điều chỉnh");
    }

    // TH3A — đánh giá định kỳ
    @Override
    public UUID startPeriodicScan() {
        LocalDate yesterday = yesterdayUtc();
        Instant from = startOfDayUtc(yesterday);
        Instant to = startOfDayUtc(yesterday.plusDays(1));
        boolean listsChanged = categoryRepository.hasCifWatchlistChanges(from, to);

        if (listsChanged) {
            log.info("TH3 — watchlists changed on {}, scanning ALL customers (T3A)", yesterday);
            return start(TriggerType.T3A, "DS mẫu điều chỉnh ngày " + yesterday);
        }
        log.info("TH3 — watchlists unchanged, scanning only customers created on {} whose score is not 7 (T3B)", yesterday);
        return start(TriggerType.T3B, "KH phát sinh ngày " + yesterday);
    }

    // =================================================================

    /**
     * Tạo bản ghi batch rồi chạy nền — caller (HTTP hoặc cron) không bị chặn.
     */
    private UUID start(TriggerType triggerType, String note) {
        UUID batchId = UUID.randomUUID();
        scanBatchRepository.save(ScanBatch.builder()
                .id(batchId)
                .triggerType(triggerType)
                .status(BatchStatus.ENQUEUING)
                .note(note)
                .build());

        pcrtBatchExecutor.execute(() -> runBatch(batchId));
        return batchId;
    }

    /**
     * Chạy B3→B5 của một batch đã có trên thread nền. Dùng cho batch do nơi khác nạp (T1R).
     */
    public void runBatchAsync(UUID batchId) {
        pcrtBatchExecutor.execute(() -> runBatch(batchId));
    }

    /**
     * B1 → B5. Chạy trên thread nền.
     */
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
            log.info("Batch {} COMPLETED — enqueued {}, processed {}, matched {}",
                    batchId, batch.getEnqueuedCount(), batch.getProcessedCount(), batch.getMatchedCount());

        } catch (RuntimeException e) {
            log.error("Batch {} FAILED: {}", batchId, e.getMessage(), e);
            batch.setStatus(BatchStatus.FAILED);
            batch.setFinishedAt(Instant.now());
            batch.setNote(truncate(e.getMessage()));
            scanBatchRepository.save(batch);
        }
    }

    private int enqueue(ScanBatch batch, int pageSize) {
//        if (batch.getTriggerType() == TriggerType.T1R) {
//            throw new IllegalStateException(
//                    "Batch T1R chết khi đang nạp hàng đợi — kích hoạt lại lệnh quét ngược");
//        }
        if (batch.getTriggerType() == TriggerType.T3B) {
            LocalDate yesterday = yesterdayUtc();
            return enqueueService.enqueueChangedYesterday(
                    batch.getId(), TriggerType.T3B, pageSize,
                    startOfDayUtc(yesterday), startOfDayUtc(yesterday.plusDays(1)));
        }
        return enqueueService.enqueueAllActive(batch.getId(), batch.getTriggerType(), pageSize);
    }

    /** Ngày liền trước, theo UTC — DB và server đều chạy UTC. */
    private static LocalDate yesterdayUtc() {
        return LocalDate.now(ZoneOffset.UTC).minusDays(1);
    }

    private static Instant startOfDayUtc(LocalDate date) {
        return date.atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    private void process(ScanBatch batch, int pageSize) {
        WatchlistSnapshot lists = blackListService.freshSnapshot();
        boolean blacklistOnly = batch.getTriggerType().isBlacklistOnly();

        batch.setProcessedCount(
                (int) customerScanEventRepo.countByScanBatchIdAndStatus(batch.getId(), ScanStatus.PROCESSED));
        batch.setMatchedCount((int) riskResultRepository.countByScanBatchId(batch.getId()));

        while (true) {
            List<CustomerScanEvent> page = customerScanEventRepo.findPendingBatch(batch.getId(), pageSize);
            if (page.isEmpty()) {
                break;
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
                    log.error("Batch {} — error while processing CIF {}: {}",
                            batch.getId(), queued.getCif(), e.getMessage(), e);
                }
            }

            if (processedInPage == 0) {
                throw new IllegalStateException(
                        "Không xử lý được bản ghi nào trong trang, dừng để tránh vòng lặp vô hạn");
            }
            scanBatchRepository.save(batch);
        }
    }

    public List<UUID> resumeUnfinished() {
        List<ScanBatch> unfinished = scanBatchRepository.findByStatusInOrderByStartedAtAsc(
                List.of(BatchStatus.ENQUEUING, BatchStatus.PROCESSING));

        for (ScanBatch batch : unfinished) {
            long remaining = customerScanEventRepo.countByScanBatchIdAndStatus(batch.getId(), ScanStatus.PENDING);
            log.info("Resuming batch {} ({}), {} customers still pending",
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
