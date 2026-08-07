package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.repository.CustomerIdentityRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import dev.hieunv.riskassessment.service.CustomerIdentityService;
import dev.hieunv.riskassessment.service.DirectionBenchmarkService;
import dev.hieunv.riskassessment.service.ReverseCandidateFinder;
import dev.hieunv.riskassessment.service.ReverseScanService;
import dev.hieunv.riskassessment.service.impl.CustomerIdentityServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pcrt")
@RequiredArgsConstructor
public class ReverseScanController {

    private final CustomerIdentityService identitySyncService;
    private final CustomerIdentityRepository CifIdentityService;
    private final WatchlistEntryRepository entryRepository;
    private final ReverseScanService reverseScanService;
    private final ReverseCandidateFinder candidateFinder;
    private final DirectionBenchmarkService benchmarkService;

    /**
     * Nạp lần đầu bản chiếu định danh từ Core. Chạy một lần trước khi quét ngược dùng được.
     */
    @PostMapping("/identity-mirror/sync")
    public ResponseEntity<CustomerIdentityServiceImpl.SyncResult> fullSync() {
        return ResponseEntity.ok(identitySyncService.fullSync());
    }

    @PostMapping("/identity-mirror/sync-delta")
    public ResponseEntity<CustomerIdentityServiceImpl.SyncResult> deltaSync() {
        return ResponseEntity.ok(identitySyncService.syncDelta());
    }

    @GetMapping("/identity-mirror")
    public ResponseEntity<Map<String, Object>> mirrorStats() {
        return ResponseEntity.ok(Map.of(
                "rows", CifIdentityService.count(),
                "scanTargets", CifIdentityService.countScanTargets(),
                "latestCoreUpdatedAt",
                CifIdentityService.findLatestCoreUpdatedAt().map(Object::toString).orElse("-")));
    }

    @PostMapping("/scans/blacklist-reverse")
    public ResponseEntity<Map<String, Object>> startReverse(@RequestParam(defaultValue = "60") long sinceMinutes) {
        UUID batchId = reverseScanService.start(Instant.now().minus(Duration.ofMinutes(sinceMinutes)));
        return ResponseEntity.accepted().body(Map.of("batchId", batchId, "triggerType", "T1R"));
    }

    @PostMapping("/bench/directions")
    public ResponseEntity<DirectionBenchmarkService.BenchmarkReport> benchmark(
            @RequestParam(defaultValue = "3") int entries) {
        return ResponseEntity.ok(benchmarkService.run(entries));
    }

    @PostMapping("/bench/reset")
    public ResponseEntity<Map<String, Object>> resetLab() {
        return ResponseEntity.ok(benchmarkService.reset());
    }

    @GetMapping(value = "/bench/explain/{entryId}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> explain(@PathVariable Long entryId) {
        return ResponseEntity.ok(candidateFinder.explain(entryRepository.findById(entryId)
                .orElseThrow(() -> new NoSuchElementException("Không có bản ghi #" + entryId))));
    }
}
