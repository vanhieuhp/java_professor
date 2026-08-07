package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.dto.AddWatchlistEntryRequest;
import dev.hieunv.riskassessment.dto.CoreDispatchStats;
import dev.hieunv.riskassessment.dto.ScanBatchResponse;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.entity.ScanBatch;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import dev.hieunv.riskassessment.repository.ScanBatchRepository;
import dev.hieunv.riskassessment.service.BatchScanService;
import dev.hieunv.riskassessment.service.CoreDispatchService;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import dev.hieunv.riskassessment.service.WatchlistAdminService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Điều khiển và quan sát batch — TH1, TH3, và tiến độ của chúng. */
@RestController
@RequestMapping("/api/v1/pcrt")
@RequiredArgsConstructor
public class ScanController {

    private final BatchScanService batchScanService;
    private final ScanBatchRepository scanBatchRepository;
    private final CustomerScanEventRepository scanQueueRepository;
    private final CustomerRiskResultRepository riskResultRepository;
    private final WatchlistAdminService watchlistAdminService;
    private final CoreDispatchService coreDispatchService;
    private final PcrtConfigService configService;

    /** TH1 — chạy tay. Thực tế được kích hoạt tự động khi DS đen bị sửa. */
    @PostMapping("/scans/blacklist")
    public ResponseEntity<Map<String, Object>> startBlacklistScan() {
        UUID batchId = batchScanService.startBlacklistScan();
        return ResponseEntity.accepted().body(Map.of("batchId", batchId, "triggerType", "T1"));
    }

    /** TH3 — chạy tay. Thực tế chạy theo cron đọc từ {@code pcrt_config}. */
    @PostMapping("/scans/periodic")
    public ResponseEntity<Map<String, Object>> startPeriodicScan() {
        UUID batchId = batchScanService.startPeriodicScan();
        return ResponseEntity.accepted().body(Map.of("batchId", batchId));
    }

    @GetMapping("/scans/{batchId}")
    public ResponseEntity<ScanBatchResponse> batch(@PathVariable UUID batchId) {
        return scanBatchRepository.findById(batchId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/scans")
    public ResponseEntity<List<ScanBatchResponse>> recentBatches(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(scanBatchRepository.findAllByOrderByStartedAtDesc(Limit.of(limit))
                .stream().map(this::toResponse).toList());
    }

    /** Các khách hàng bị đánh dấu rủi ro trong một lần quét. */
    @GetMapping("/scans/{batchId}/results")
    public ResponseEntity<List<CustomerRiskResult>> batchResults(@PathVariable UUID batchId) {
        return ResponseEntity.ok(riskResultRepository.findByScanBatchIdOrderByIdAsc(batchId));
    }

    /** Kết quả mới nhất của một khách hàng. */
    @GetMapping("/customers/{cif}/risk")
    public ResponseEntity<CustomerRiskResult> latestRisk(@PathVariable String cif) {
        return riskResultRepository.findByCifAndLatestTrue(cif)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Thêm bản ghi vào một danh sách. Nếu là DS đen, đây chính là sự kiện
     * "DS đen được điều chỉnh" và TH1 sẽ tự khởi động trong vài giây.
     */
    @PostMapping("/watchlists/entries")
    public ResponseEntity<Map<String, Object>> addEntry(@Valid @RequestBody AddWatchlistEntryRequest request) {
        Long id = watchlistAdminService.addEntry(request);
        return ResponseEntity.ok(Map.of("entryId", id, "categoryCode", request.getCategoryCode()));
    }

    /** Tình trạng hàng đợi gửi Core + trạng thái cầu dao. */
    @GetMapping("/core-dispatch")
    public ResponseEntity<CoreDispatchStats> dispatchStats() {
        return ResponseEntity.ok(coreDispatchService.currentStats());
    }

    /** Chạy một lượt gửi ngay, không đợi chu kỳ job. */
    @PostMapping("/core-dispatch/run")
    public ResponseEntity<CoreDispatchStats> runDispatch() {
        return ResponseEntity.ok(coreDispatchService.dispatchDue());
    }

    /**
     * Đưa các kết quả FAILED trở lại hàng đợi. Dùng sau khi đã sửa nguyên nhân gốc —
     * phần lớn là sai cấu hình ({@code core.base-url}, đường dẫn, contract), không phải
     * dữ liệu hỏng.
     */
    @PostMapping("/core-dispatch/requeue-failed")
    public ResponseEntity<Map<String, Object>> requeueFailed() {
        return ResponseEntity.ok(Map.of("requeued", coreDispatchService.requeueFailed()));
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, String>> config() {
        return ResponseEntity.ok(Map.of(
                PcrtConfigService.KEY_TH3_CRON, configService.get(PcrtConfigService.KEY_TH3_CRON, "-"),
                PcrtConfigService.KEY_PAGE_SIZE, configService.get(PcrtConfigService.KEY_PAGE_SIZE, "-"),
                PcrtConfigService.KEY_TH1_AUTO, configService.get(PcrtConfigService.KEY_TH1_AUTO, "-"),
                PcrtConfigService.KEY_RESUME_ON_STARTUP,
                configService.get(PcrtConfigService.KEY_RESUME_ON_STARTUP, "-")));
    }

    /** Đổi lịch chạy TH3 — có hiệu lực ở lần tính lịch kế tiếp, không cần restart. */
    @PostMapping("/config/{key}")
    public ResponseEntity<Map<String, String>> updateConfig(@PathVariable String key,
                                                            @RequestParam String value) {
        int updated = configService.set(key, value);
        return updated == 0
                ? ResponseEntity.notFound().build()
                : ResponseEntity.ok(Map.of(key, value));
    }

    private ScanBatchResponse toResponse(ScanBatch b) {
        return ScanBatchResponse.builder()
                .batchId(b.getId())
                .triggerType(b.getTriggerType())
                .status(b.getStatus())
                .enqueuedCount(b.getEnqueuedCount())
                .processedCount(b.getProcessedCount())
                .matchedCount(b.getMatchedCount())
                .pendingCount(scanQueueRepository.countByScanBatchIdAndStatus(b.getId(), ScanStatus.PENDING))
                .startedAt(b.getStartedAt())
                .finishedAt(b.getFinishedAt())
                .note(b.getNote())
                .build();
    }
}
