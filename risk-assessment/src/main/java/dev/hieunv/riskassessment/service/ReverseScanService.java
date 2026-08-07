package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.entity.ScanBatch;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.repository.CoreCustomerRepository;
import dev.hieunv.riskassessment.repository.ScanBatchRepository;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * TH1 đi chiều ngược — B1/B2 thay mới, B3/B4/B5 giữ nguyên.
 *
 * <h2>Đây không phải là viết lại pipeline</h2>
 * Hàng đợi, cơ chế resume, biên bản mỗi CIF một dòng, hàng đợi gửi Core với backoff — tất cả
 * giữ nguyên. Thứ duy nhất đổi là <b>cái vòi đổ nước vào hàng đợi</b>: thay vì rót toàn bộ
 * khách hàng vào rồi lọc, chỉ rót đúng những người mà DB đã chỉ ra là có thể khớp.
 *
 * <h2>Hai lần thu hẹp, không phải một</h2>
 * <ol>
 *   <li><b>Delta của danh sách</b> — chỉ lấy các bản ghi DS đen vừa thêm/sửa. 99 bản ghi
 *       không đổi đã được so khớp ở lần quét trước; so lại chúng không thể ra kết quả mới.</li>
 *   <li><b>Đổi chiều truy vấn</b> — với mỗi bản ghi đó, hỏi DB "ai khớp" thay vì hỏi từng
 *       khách hàng "có khớp không".</li>
 * </ol>
 * Hai phép này độc lập nhau. Phép thứ nhất áp dụng được ngay cả khi vẫn giữ chiều xuôi.
 *
 * <h2>Khi nào phải quay về chiều xuôi</h2>
 * Một bản ghi có họ tên + ngày sinh quá phổ biến sẽ khớp hàng chục nghìn người. Khi đó quét
 * ngược không còn rẻ, và quan trọng hơn: <b>không được phép cắt bớt kết quả</b>. Bỏ sót một
 * người trong danh sách trừng phạt là loại sai nghiêm trọng nhất của hệ thống này. Nên chạm
 * trần thì bỏ toàn bộ đường ngược và quét xuôi — chậm nhưng đầy đủ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReverseScanService {

    private final WatchlistCategoryRepository categoryRepository;
    private final WatchlistEntryRepository entryRepository;
    private final CoreCustomerRepository coreCustomerRepository;
    private final ScanBatchRepository scanBatchRepository;
    private final ScanQueueWriter scanQueueWriter;
    private final ReverseCandidateFinder candidateFinder;
    private final BatchScanService batchScanService;
    private final PcrtConfigService configService;

    /**
     * Nạp hàng đợi bằng chiều ngược và trả batch về ở trạng thái PROCESSING.
     * <b>Chưa</b> chạy B3 — caller quyết định chạy nền hay chạy đồng bộ.
     *
     * @param since chỉ xét các bản ghi DS đen thay đổi sau mốc này
     */
    public ReverseEnqueueReport enqueue(Instant since) {
        long startNanos = System.nanoTime();

        WatchlistCategory blacklist = categoryRepository.findBlacklistCategory()
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy DS đen đang hoạt động"));

        List<WatchlistEntry> changed = entryRepository
                .findByCategoryIdAndActiveTrueAndUpdatedAtAfterOrderByIdAsc(blacklist.getId(), since);
        long totalEntries = entryRepository.countByCategoryIdAndActiveTrue(blacklist.getId());

        UUID batchId = UUID.randomUUID();
        scanBatchRepository.save(ScanBatch.builder()
                .id(batchId)
                .triggerType(TriggerType.T1R)
                .status(BatchStatus.ENQUEUING)
                .note("Quét ngược — " + changed.size() + "/" + totalEntries + " bản ghi DS đen thay đổi")
                .build());

        // LinkedHashSet: một khách hàng có thể khớp nhiều bản ghi vừa đổi cùng lúc, mà
        // uq_queue_batch_cif chỉ cho mỗi CIF một dòng trong một batch. Khử trùng ở đây rẻ
        // hơn để DB ném lỗi rồi bắt. Giữ thứ tự để lần chạy nào cũng cho cùng một kết quả.
        Set<String> candidateCifs = new LinkedHashSet<>();
        List<EntryProbe> probes = new ArrayList<>();
        boolean fallback = false;

        for (WatchlistEntry entry : changed) {
            ReverseCandidateFinder.Candidates c = candidateFinder.find(entry);
            probes.add(EntryProbe.builder()
                    .entryId(entry.getId())
                    .candidates(c.isTooBroad() ? -1 : c.getCifs().size())
                    .queryMicros(c.getElapsedMicros())
                    .tooBroad(c.isTooBroad())
                    .build());
            if (c.isTooBroad()) {
                fallback = true;
                break;
            }
            candidateCifs.addAll(c.getCifs());
        }

        if (fallback) {
            scanBatchRepository.findById(batchId).ifPresent(b -> {
                b.setStatus(BatchStatus.FAILED);
                b.setFinishedAt(Instant.now());
                b.setNote("Có bản ghi quá rộng — chuyển sang quét xuôi");
                scanBatchRepository.save(b);
            });
            UUID forwardId = batchScanService.startBlacklistScan();
            log.warn("Reverse scan gave up, triggered forward scan batch {}", forwardId);
            return ReverseEnqueueReport.builder()
                    .batchId(forwardId).entriesChanged(changed.size()).entriesTotal(totalEntries)
                    .candidateCount(0).probes(probes).fellBackToForward(true)
                    .elapsedMillis((System.nanoTime() - startNanos) / 1_000_000)
                    .build();
        }

        int enqueued = enqueueCandidates(batchId, candidateCifs);

        ScanBatch batch = scanBatchRepository.findById(batchId).orElseThrow();
        batch.setEnqueuedCount(enqueued);
        batch.setStatus(BatchStatus.PROCESSING);
        scanBatchRepository.save(batch);

        long millis = (System.nanoTime() - startNanos) / 1_000_000;
        log.info("Reverse scan: {}/{} blacklist entries changed → {} candidates, {} enqueued in {} ms",
                changed.size(), totalEntries, candidateCifs.size(), enqueued, millis);

        return ReverseEnqueueReport.builder()
                .batchId(batchId).entriesChanged(changed.size()).entriesTotal(totalEntries)
                .candidateCount(candidateCifs.size()).probes(probes).fellBackToForward(false)
                .elapsedMillis(millis)
                .build();
    }

    public UUID start(Instant since) {
        ReverseEnqueueReport report = enqueue(since);
        if (!report.isFellBackToForward()) {
            batchScanService.runBatchAsync(report.getBatchId());
        }
        return report.getBatchId();
    }

    /**
     * Đọc dữ liệu đầy đủ của các ứng viên từ Core rồi ghi hàng đợi, theo trang.
     * <p>
     * Vẫn phải gọi Core — bản chiếu chỉ có 4 trường định danh đã chuẩn hóa, còn hàng đợi cần
     * bản thô để ghi biên bản và cần quốc tịch/nghề nghiệp/chức vụ cho K2-K4. Khác biệt nằm ở
     * số lượng: vài chục thay vì 5 triệu.
     */
    private int enqueueCandidates(UUID batchId, Set<String> cifs) {
        if (cifs.isEmpty()) {
            return 0;
        }
        int pageSize = configService.pageSize();
        List<String> all = List.copyOf(cifs);
        int total = 0;
        for (int from = 0; from < all.size(); from += pageSize) {
            List<String> chunk = all.subList(from, Math.min(from + pageSize, all.size()));
            List<CoreCustomer> customers = coreCustomerRepository.findByCifIn(chunk);
            if (customers.size() != chunk.size()) {
                // Bản chiếu chỉ ra một CIF mà Core không còn — bản chiếu đã lệch.
                // Không phải lỗi chặn: người đó biến mất khỏi Core thì cũng không cần chấm.
                // Nhưng phải kêu lên, vì đây là triệu chứng của việc đồng bộ đang hỏng.
                log.warn("Mirror drift: {} CIFs not found in Core", chunk.size() - customers.size());
            }
            total += scanQueueWriter.savePage(batchId, TriggerType.T1R, customers);
        }
        return total;
    }

    @Builder
    @Getter
    public static class ReverseEnqueueReport {
        private final UUID batchId;
        private final int entriesChanged;
        private final long entriesTotal;
        private final int candidateCount;
        private final boolean fellBackToForward;
        private final long elapsedMillis;
        private final List<EntryProbe> probes;
    }

    /** Số liệu của một câu query ngược — mỗi bản ghi danh sách một dòng. */
    @Builder
    @Getter
    public static class EntryProbe {
        private final Long entryId;
        /** -1 = quá rộng, không dùng được. */
        private final int candidates;
        private final long queryMicros;
        private final boolean tooBroad;
    }
}
