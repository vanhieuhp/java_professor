package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.AddWatchlistEntryRequest;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.entity.ScanBatch;
import dev.hieunv.riskassessment.repository.CoreCustomerRepository;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.ScanBatchRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import dev.hieunv.riskassessment.service.impl.CustomerIdentityServiceImpl;
import dev.hieunv.riskassessment.service.impl.WatchlistServiceImpl;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Đo đối chứng hai chiều trên cùng một dữ liệu, cùng một thay đổi danh sách.
 *
 * <h2>Kịch bản</h2>
 * Thêm N bản ghi vào DS đen — mỗi bản ghi dựng từ một khách hàng có thật trong Core, xoay
 * vòng qua cả ba hình dạng khớp mà luật cho phép (chỉ Số GTTT / họ tên + ngày sinh /
 * họ tên + SĐT). Nhờ vậy <b>biết trước đáp án</b>: đúng N khách hàng đó phải bị bắt.
 * <p>
 * Rồi chạy cả hai chiều và so ba thứ: thời gian, số khách hàng phải xử lý, và — quan trọng
 * hơn cả hai — <b>tập CIF bắt được có giống nhau không</b>. Một phép tối ưu nhanh hơn nhưng
 * bỏ sót người thì không phải tối ưu, mà là lỗi.
 *
 * <h2>Vì sao chạy chiều ngược TRƯỚC</h2>
 * Quét xuôi đọc toàn bộ {@code core.wallet_customer} và làm nóng buffer cache của Postgres.
 * Chạy nó trước rồi mới đo chiều ngược là tự làm đẹp cho chiều ngược. Đảo lại thứ tự để phép
 * đo nghiêng về phía bất lợi cho kết luận mình muốn chứng minh.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DirectionBenchmarkService {

    private final CoreCustomerRepository coreCustomerRepository;
    private final CustomerRiskResultRepository riskResultRepository;
    private final ScanBatchRepository scanBatchRepository;
    private final WatchlistEntryRepository entryRepository;
    private final WatchlistAdminService watchlistAdminService;
    private final WatchlistServiceImpl blackListServiceImpl;
    private final CustomerIdentityServiceImpl identitySyncService;
    private final ScanEnqueueService enqueueService;
    private final BatchScanService batchScanService;
    private final ReverseScanService reverseScanService;
    private final BlacklistChangeDetector blacklistChangeDetector;
    private final PcrtConfigService configService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Chạy phép đo. Hai công tắc phải tắt trong suốt phép đo, và cả hai đều học được từ một
     * lần đo hỏng:
     * <ul>
     *   <li><b>Trigger TH1 tự động</b> — detector thấy DS đen đổi sẽ tự chạy thêm một lần quét
     *       xuôi song song, ăn CPU của chính phép đo.</li>
     *   <li><b>Job gửi Core</b> — đây là cái đã làm hỏng lần đo đầu tiên. Chiều ngược chạy
     *       trước, sinh 6 kết quả điểm 7; job gửi chạy nền và Core <b>khóa ví</b> 6 người đó;
     *       tới lượt chiều xuôi thì họ không còn là KH Active nên không được quét, và chiều
     *       xuôi báo "không tìm thấy ai". Con số thì vẫn ra, nhưng nó đo một thế giới khác với
     *       thế giới mà chiều ngược đã đo. Một phép đo so hai nhánh phải giữ dữ liệu đứng yên
     *       giữa hai nhánh — nếu không nó nói dối mà vẫn trông rất thuyết phục.</li>
     * </ul>
     */
    public BenchmarkReport run(int newEntries) {
        reset();

        String previousAuto = configService.get(PcrtConfigService.KEY_TH1_AUTO, "true");
        String previousDispatch = configService.get("core.dispatch.enabled", "true");
        configService.set(PcrtConfigService.KEY_TH1_AUTO, "false");
        configService.set("core.dispatch.enabled", "false");
        try {
            return measure(newEntries);
        } finally {
            blacklistChangeDetector.resyncBaseline();
            configService.set(PcrtConfigService.KEY_TH1_AUTO, previousAuto);
            configService.set("core.dispatch.enabled", previousDispatch);
        }
    }

    /**
     * Đưa phòng thí nghiệm về mốc gốc để đo lại được. CHỈ dùng với Core giả.
     * <p>
     * Không phải "dọn rác cho đẹp": nếu không reset, lần đo thứ hai bắt đầu từ một thế giới
     * mà lần đo thứ nhất đã sửa (khách hàng bị khóa ví, bản ghi DS đen còn sót), và hai lần
     * đo không so sánh được với nhau.
     */
    public Map<String, Object> reset() {
        // Trả các CIF mà Core giả đã khóa về Active. Chỉ đụng tới CIF có trong nhật ký lệnh —
        // CIF-SKIP-LOCKED được seed sẵn ở trạng thái LOCKED có chủ ý, phải để nguyên.
        int unlocked = jdbcTemplate.update("""
                UPDATE core.wallet_customer
                SET status = 'ACTIVE', risk_score = NULL
                WHERE cif IN (SELECT DISTINCT cif FROM core.risk_update_log)
                """);
        jdbcTemplate.update("DELETE FROM core.risk_update_log");

        int entries = jdbcTemplate.update("DELETE FROM watchlist_entry WHERE source = 'BENCHMARK'");
        jdbcTemplate.update("DELETE FROM customer_risk_result");
        jdbcTemplate.update("DELETE FROM customer_scan_queue");
        jdbcTemplate.update("DELETE FROM scan_batch");
        jdbcTemplate.update("UPDATE watchlist_category SET entries_changed_at = now() WHERE is_blacklist");

        blackListServiceImpl.reload();
        blacklistChangeDetector.resyncBaseline();
        // Bản chiếu phải đồng bộ lại: lệnh UPDATE ở trên không đi qua update_time của Core
        // nên đồng bộ delta sẽ không thấy. Đây là lý do bản chiếu luôn cần một đường nạp
        // toàn bộ, không chỉ đường delta.
        CustomerIdentityServiceImpl.SyncResult sync = identitySyncService.fullSync();

        log.warn("Lab reset: unlocked {} CIFs, deleted {} benchmark blacklist entries", unlocked, entries);
        return Map.of("unlockedCifs", unlocked, "benchmarkEntriesRemoved", entries,
                "mirrorScanTargets", sync.getScanTargetsInMirror());
    }

    private BenchmarkReport measure(int newEntries) {
        // Mốc delta lấy từ đồng hồ CỦA DATABASE, không phải của JVM. watchlist_entry.updated_at
        // do DB sinh bằng now(), nên so nó với Instant.now() của Java là so hai đồng hồ khác
        // nhau — lệch vài chục mili-giây là đủ để bỏ sót bản ghi vừa thêm.
        Instant since = jdbcTemplate.queryForObject("SELECT now() - interval '1 second'", Timestamp.class)
                .toInstant();

        Set<String> expectedCifs = seedBlacklistEntries(newEntries);
        blackListServiceImpl.reload();

        ReverseRun reverse = runReverse(since);
        ForwardRun forward = runForward();

        Set<String> reverseMatched = matchedCifs(reverse.batchId);
        Set<String> forwardMatched = matchedCifs(forward.batchId);

        // Quét xuôi so khớp với TOÀN BỘ DS đen nên bắt thêm cả những người trùng các bản ghi
        // có từ trước (CIF-BL-001..003). Quét ngược chỉ trả lời "ai khớp phần VỪA THAY ĐỔI".
        // Đó là khác biệt về câu hỏi, không phải về độ chính xác — nên tiêu chí đúng/sai là:
        // cả hai chiều có bắt đủ tập đáp án đã biết trước hay không.
        Set<String> forwardOnly = new TreeSet<>(forwardMatched);
        forwardOnly.removeAll(reverseMatched);
        Set<String> reverseOnly = new TreeSet<>(reverseMatched);
        reverseOnly.removeAll(forwardMatched);

        return BenchmarkReport.builder()
                .customersInCore(coreCustomerRepository.countScanTargets())
                .newBlacklistEntries(newEntries)
                .expectedCifs(new TreeSet<>(expectedCifs))
                .forward(forward)
                .reverse(reverse)
                .forwardMatched(new TreeSet<>(forwardMatched))
                .reverseMatched(new TreeSet<>(reverseMatched))
                .forwardOnly(forwardOnly)
                .reverseOnly(reverseOnly)
                .forwardFoundAllExpected(forwardMatched.containsAll(expectedCifs))
                .reverseFoundAllExpected(reverseMatched.containsAll(expectedCifs))
                .speedup(reverse.totalMillis == 0 ? -1
                        : (double) forward.totalMillis / reverse.totalMillis)
                .build();
    }

    /**
     * Dựng N bản ghi DS đen từ N khách hàng có thật, xoay vòng qua ba hình dạng khớp.
     * <p>
     * Cố ý không dùng dữ liệu ngẫu nhiên: đáp án phải biết trước thì phép đo mới kiểm chứng
     * được tính đúng, chứ không chỉ đo được tốc độ.
     */
    private Set<String> seedBlacklistEntries(int n) {
        List<CoreCustomer> victims = new ArrayList<>();
        long cursor = 10_000L;   // bỏ qua khối CIF cài sẵn ở đầu bảng
        while (victims.size() < n) {
            List<CoreCustomer> page = coreCustomerRepository.findScanTargetsAfter(cursor, n - victims.size());
            if (page.isEmpty()) {
                break;
            }
            victims.addAll(page);
            cursor = page.get(page.size() - 1).getId();
        }

        Set<String> cifs = new LinkedHashSet<>();
        for (int i = 0; i < victims.size(); i++) {
            CoreCustomer v = victims.get(i);
            AddWatchlistEntryRequest.AddWatchlistEntryRequestBuilder r = AddWatchlistEntryRequest.builder()
                    .categoryCode("BLACKLIST")
                    .source("BENCHMARK")
                    .sourceRef("BENCH-" + v.getCif());

            switch (i % 3) {
                // Vế 1 của luật: chỉ Số GTTT là đủ.
                case 0 -> r.idNumber(v.getIdNumber());
                // Vế 2: đủ 2/4 trường trên cùng một bản ghi.
                case 1 -> r.fullName(v.getFullName()).dob(v.getDob());
                default -> r.fullName(v.getFullName()).phone(v.getPhone());
            }
            watchlistAdminService.addEntry(r.build());
            cifs.add(v.getCif());
        }
        log.warn("Added {} blacklist entries for the benchmark — expected answer: {}", cifs.size(), cifs);
        return cifs;
    }

    /** Chiều xuôi: nạp toàn bộ KH vào hàng đợi rồi chấm từng người. */
    private ForwardRun runForward() {
        UUID batchId = UUID.randomUUID();
        scanBatchRepository.save(ScanBatch.builder()
                .id(batchId).triggerType(TriggerType.T1)
                .status(BatchStatus.ENQUEUING).note("Đo đối chứng — chiều xuôi").build());

        long t0 = System.nanoTime();
        int enqueued = enqueueService.enqueueAllActive(batchId, TriggerType.T1, configService.pageSize());
        long t1 = System.nanoTime();

        ScanBatch batch = scanBatchRepository.findById(batchId).orElseThrow();
        batch.setEnqueuedCount(enqueued);
        batch.setStatus(BatchStatus.PROCESSING);
        scanBatchRepository.save(batch);

        long t2 = System.nanoTime();
        batchScanService.runBatch(batchId);   // đồng bộ, không qua executor
        long t3 = System.nanoTime();

        return ForwardRun.builder()
                .batchId(batchId)
                .enqueuedCustomers(enqueued)
                .entriesCompared(entryRepository.countByCategoryIdAndActiveTrue(blacklistId()))
                .enqueueMillis((t1 - t0) / 1_000_000)
                .processMillis((t3 - t2) / 1_000_000)
                .totalMillis((t1 - t0 + t3 - t2) / 1_000_000)
                .build();
    }

    /** Chiều ngược: hỏi DB ai khớp từng bản ghi vừa đổi, chỉ nạp bấy nhiêu người. */
    private ReverseRun runReverse(Instant since) {
        long t0 = System.nanoTime();
        ReverseScanService.ReverseEnqueueReport report = reverseScanService.enqueue(since);
        long t1 = System.nanoTime();

        long t2 = System.nanoTime();
        batchScanService.runBatch(report.getBatchId());
        long t3 = System.nanoTime();

        return ReverseRun.builder()
                .batchId(report.getBatchId())
                .entriesChanged(report.getEntriesChanged())
                .entriesTotal(report.getEntriesTotal())
                .enqueuedCustomers(report.getCandidateCount())
                .fellBackToForward(report.isFellBackToForward())
                .probes(report.getProbes())
                .enqueueMillis((t1 - t0) / 1_000_000)
                .processMillis((t3 - t2) / 1_000_000)
                .totalMillis((t1 - t0 + t3 - t2) / 1_000_000)
                .build();
    }

    private Long blacklistId() {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM watchlist_category WHERE is_blacklist AND active", Long.class);
    }

    private Set<String> matchedCifs(UUID batchId) {
        return riskResultRepository.findByScanBatchIdOrderByIdAsc(batchId).stream()
                .map(CustomerRiskResult::getCif)
                .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
    }

    @Builder
    @Getter
    public static class BenchmarkReport {
        private final long customersInCore;
        private final int newBlacklistEntries;
        private final Set<String> expectedCifs;

        private final ForwardRun forward;
        private final ReverseRun reverse;

        private final Set<String> forwardMatched;
        private final Set<String> reverseMatched;
        /** Chiều xuôi bắt thêm ai — là các bản ghi DS đen CŨ, không phải chiều ngược bỏ sót. */
        private final Set<String> forwardOnly;
        /** Chiều ngược bắt thêm ai — phải luôn rỗng, khác rỗng là có lỗi. */
        private final Set<String> reverseOnly;

        private final boolean forwardFoundAllExpected;
        private final boolean reverseFoundAllExpected;
        private final double speedup;
    }

    @Builder
    @Getter
    public static class ForwardRun {
        private final UUID batchId;
        private final int enqueuedCustomers;
        private final long entriesCompared;
        private final long enqueueMillis;
        private final long processMillis;
        private final long totalMillis;
    }

    @Builder
    @Getter
    public static class ReverseRun {
        private final UUID batchId;
        private final int entriesChanged;
        private final long entriesTotal;
        private final int enqueuedCustomers;
        private final boolean fellBackToForward;
        private final long enqueueMillis;
        private final long processMillis;
        private final long totalMillis;
        private final List<ReverseScanService.EntryProbe> probes;
    }
}
