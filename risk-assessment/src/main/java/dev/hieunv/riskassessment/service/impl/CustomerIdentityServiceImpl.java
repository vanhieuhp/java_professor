package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.core.CoreCustomer;
import dev.hieunv.riskassessment.core.service.CoreCustomerServiceImpl;
import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.UpsertCustomerRequest;
import dev.hieunv.riskassessment.entity.CustomerIdentity;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import dev.hieunv.riskassessment.mapper.CustomerMapper;
import dev.hieunv.riskassessment.repository.CustomerIdentityRepository;
import dev.hieunv.riskassessment.service.CustomerIdentityService;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import dev.hieunv.riskassessment.utils.Normalizer;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIdentityServiceImpl implements CustomerIdentityService {

    /** Trạng thái của Core giả — vẫn còn hiệu lực trên đường sự kiện, xem {@link #isScanTarget}. */
    private static final Set<String> MOCK_CORE_SCAN_TARGET_STATUSES = Set.of("ACTIVE", "APPROVED");

    private static final short NORMALIZER_VERSION = 1;

    private final CoreCustomerServiceImpl customerServiceImpl;
    private final CustomerIdentityRepository identityRepository;
    private final PcrtConfigService configService;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    private TransactionTemplate pageTransaction;

    @PostConstruct
    void initPageTransaction() {
        pageTransaction = new TransactionTemplate(transactionManager);
    }

    public static final String KEY_WATERMARK = "identity.sync.watermark";

    @Override
    public SyncResult fullSync() {
        int pageSize = configService.getInt("identity.sync.page.size", 2000);
        return run("toàn bộ", afterId -> customerServiceImpl.nextPage(afterId, pageSize));
    }

    /**
     * Đồng bộ delta — nay chỉ bắt được khách hàng MỚI MỞ VÍ, không bắt được thay đổi hồ sơ.
     * <p>
     * Core không có cột nào ghi lại lần sửa cuối ({@code upd_seq} và {@code last_audit_id} là
     * hằng số trên toàn bảng), nên mốc duy nhất so được là ngày mở ví. Hệ quả phải nói rõ:
     * lưới hứng cho sự kiện TH2 bị mất <b>không còn</b>. Một khách hàng đổi họ tên mà sự kiện
     * Kafka thất lạc sẽ nằm sai trong bản chiếu cho tới lần đồng bộ TOÀN BỘ kế tiếp.
     */
    @Override
    public SyncResult syncDelta() {
        int pageSize = configService.getInt("identity.sync.page.size", 2000);
        Instant since = readWatermark().minusSeconds(60);
        log.info("Delta identity sync from watermark {} — new enrollments only, profile edits are invisible "
                + "to this path because Core has no modification timestamp", since);
        return run("delta", afterId -> customerServiceImpl.nextEnrolledAfterPage(since, afterId, pageSize));
    }

    private SyncResult run(String mode, Function<String, List<CoreCustomer>> pageReader) {
        long startedNanos = System.nanoTime();

        // Chốt mốc mới TRƯỚC khi đọc, và ghi lại sau khi xong. Lấy mốc lúc kết thúc sẽ bỏ sót
        // mọi thay đổi phát sinh trong lúc lượt này đang chạy — với 5 triệu dòng thì đó là
        // vài phút thay đổi biến mất không dấu vết.
        Instant runStartedAt = customerServiceImpl.coreNow();
        String cursor = "";
        int total = 0;
        int stale = 0;

        while (true) {
            List<CoreCustomer> page = pageReader.apply(cursor);
            if (page.isEmpty()) {
                break;
            }
            stale += upsertPage(page);
            total += page.size();
            cursor = page.get(page.size() - 1).getCif();
        }

        // Job đọc Core rồi ghi bản chiếu; giữa hai việc đó đường realtime ghi xen vào được.
        // Số này là số lần điều đó thật sự xảy ra — bằng 0 thì hai đường không giẫm chân nhau,
        // khác 0 thì chốt thứ tự vừa làm đúng việc của nó chứ không phải có gì hỏng.
        if (stale > 0) {
            log.info("Identity sync ({}): {} rows skipped by the ordering guard because the mirror "
                    + "holds data newer than Core", mode, stale);
        }

        // Chỉ dời mốc khi đã chạy hết. Ném ngoại lệ giữa chừng thì mốc giữ nguyên và lượt sau
        // làm lại từ đầu — chậm, nhưng không bỏ sót.
        configService.set(KEY_WATERMARK, runStartedAt.toString());

        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        long scanTargets = identityRepository.countScanTargets();
        log.info("Identity sync ({}) finished: {} rows in {} ms — mirror holds {} scan-target customers",
                mode, total, millis, scanTargets);
        return SyncResult.builder()
                .mode(mode)
                .rowsSynced(total)
                .scanTargetsInMirror(scanTargets)
                .elapsedMillis(millis)
                .build();
    }

    @Transactional
    @Override
    public boolean upsertFromRequest(CustomerEvaluateRequest request) {
        warnIfInFuture(request.getCif(), "updateTime", request.getUpdateTime(), "hệ thống gửi");

        UpsertCustomerRequest upsert = UpsertCustomerRequest.builder()
                .cif(request.getCif())
                .fullName(request.getFullName())
                .dob(request.getDob())
                .phone(request.getPhone())
                .idNumber(request.getIdNumber())
                .oldIdNumber(request.getOldIdNumber())
                .occurredAt(Instant.now())
                .build();

        return upsertMirror(upsert);
    }


    @Transactional
    @Override
    public boolean upsertFromEvent(CustomerChangedEvent event) {
        warnIfInFuture(event.getCif(), "occurredAt", event.getOccurredAt(), "Core");
        UpsertCustomerRequest upsert = UpsertCustomerRequest.builder()
                .cif(event.getCif())
                .scanTarget(isScanTarget(event))
                .fullName(event.getFullName())
                .dob(event.getDob())
                .phone(event.getPhone())
                .idNumber(event.getIdNumber())
                .oldIdNumber(event.getOldIdNumber())
                .occurredAt(event.getOccurredAt())
                .build();

        return upsertMirror(upsert);
    }

    private boolean upsertMirror(UpsertCustomerRequest request) {
        Instant mark = clampToNow(request.getOccurredAt());

        CustomerIdentity entity = identityRepository.findByCifForUpdate(request.getCif()).orElse(null);
        if (entity == null) {
            entity = CustomerIdentity.builder().cif(request.getCif()).build();
        } else if (entity.getCoreUpdatedAt().isAfter(mark)) {
            return false;
        }

        CustomerMapper.applyToEntity(entity, request, mark);
        identityRepository.save(entity);
        return true;
    }

    private static void applyTo(CustomerIdentity row, MirrorWrite w, Instant mark) {
        row.setScanTarget(w.scanTarget());
        row.setFullNameNorm(Normalizer.name(w.fullName()));
        row.setDob(w.dob());
        row.setPhoneNorm(Normalizer.phone(w.phone()));
        row.setIdNumberNorm(Normalizer.idNumber(w.idNumber()));
        row.setOldIdNumberNorm(Normalizer.idNumber(w.oldIdNumber()));
        // Nguyên văn cho báo cáo — xem CustomerIdentity.
        row.setFullName(w.fullName());
        row.setPhone(w.phone());
        row.setIdNumber(w.idNumber());
        row.setNormalizerVersion(NORMALIZER_VERSION);
        row.setCoreUpdatedAt(mark);
        row.setSyncedAt(Instant.now());
        // Khách hàng được tạo lại sau khi Core báo xóa: gỡ bia mộ để dòng quay lại tập quét.
        row.setDeletedAt(null);
    }

    /**
     * Dữ liệu thô của một lần ghi realtime, trước khi chuẩn hóa.
     *
     * <h2>Vì sao là record có builder chứ không phải chín tham số</h2>
     * Chín tham số mà năm trong số đó là {@code String} thì đổi chỗ hai cái bất kỳ vẫn biên
     * dịch được, vẫn chạy được, và bản chiếu lặng lẽ mang số điện thoại ở ô số CMND. Đúng cái
     * kiểu lỗi mà việc bỏ JdbcTemplate sinh ra để tránh — không có lý do gì dựng lại nó ở
     * tầng trên.
     */
    @lombok.Builder
    private record MirrorWrite(String cif,
                               boolean scanTarget,
                               String fullName,
                               LocalDate dob,
                               String phone,
                               String idNumber,
                               String oldIdNumber,
                               Instant occurredAt) {
    }

    private Instant clampToNow(Instant mark) {
        Instant now = Instant.now();
        return mark == null || mark.isAfter(now) ? now : mark;
    }

    private static void warnIfInFuture(String cif, String field, Instant mark, String source) {
        if (mark != null && mark.isAfter(Instant.now().plusSeconds(300))) {
            log.warn("CIF {} — {} {} is in the future, will be clamped to now. "
                    + "Check the clock/timezone of {}.", cif, field, mark, source);
        }
    }

    /**
     * Bia mộ — đường RA của bản chiếu, dùng cho sự kiện {@code DELETED} của Core.
     *
     * <h2>Vì sao đánh dấu chứ không xóa dòng</h2>
     * Xóa hẳn thì một sự kiện {@code UPDATED} cũ tới muộn <b>sau</b> lệnh xóa sẽ không tìm
     * thấy dòng nào để chốt thứ tự bám vào, nên nó tạo dòng mới — khách hàng đã xóa sống lại,
     * với {@code scanTarget = true}, và không có gì báo cho ai biết. Giữ dòng lại thì chốt thứ
     * tự vẫn chặn được đúng sự kiện đó.
     *
     * <h2>Ba lý do không gỡ được, nay tách riêng</h2>
     * Bản JDBC gộp cả ba vào một số đếm {@code 0} nên log phải liệt kê "không có dòng, đã gỡ,
     * hoặc bản chiếu mới hơn" và người đọc tự đoán. Đọc dòng lên trước thì biết chính xác cái
     * nào — và ba cái đó có ý nghĩa vận hành khác hẳn nhau: không có dòng nghĩa là Core báo
     * xóa một CIF mà bản chiếu chưa từng biết (đáng ngờ), còn đã gỡ từ trước chỉ là sự kiện
     * lặp (bình thường).
     *
     * @return true nếu vừa gỡ; false nếu không có dòng nào, đã gỡ từ trước, hoặc bị chốt thứ
     * tự chặn vì bản chiếu đang giữ dữ liệu mới hơn
     */
    @Transactional
    @Override
    public boolean retire(String cif, Instant occurredAt) {
        warnIfInFuture(cif, "occurredAt", occurredAt, "Core");
        Instant mark = clampToNow(occurredAt);

        CustomerIdentity row = identityRepository.findByCifForUpdate(cif).orElse(null);
        if (row == null) {
            log.warn("CIF {} — Core reported a delete but the mirror has no row", cif);
            return false;
        }
        if (row.getDeletedAt() != null) {
            log.info("CIF {} — already retired from the mirror at {}, ignoring duplicate DELETED event",
                    cif, row.getDeletedAt());
            return false;
        }
        if (row.getCoreUpdatedAt().isAfter(mark)) {
            log.warn("CIF {} — skipping retirement: the mirror holds data newer than this DELETED "
                    + "event's timestamp {}", cif, occurredAt);
            return false;
        }

        Instant now = Instant.now();
        row.setScanTarget(false);
        row.setDeletedAt(now);
        row.setCoreUpdatedAt(mark);
        row.setSyncedAt(now);
        identityRepository.save(row);

        log.warn("CIF {} removed from the scan set per Core's DELETED event", cif);
        return true;
    }

    /**
     * Đúng tập mà TH1/TH3a quét: khách hàng <b>cá nhân</b>, ví đang hoạt động.
     * <p>
     * Thiếu trường thì hiểu theo hướng <b>vẫn quét</b>. Đây là lựa chọn có chủ ý: rà soát
     * nhầm một khách hàng ngoài diện chỉ tốn công, còn loại nhầm một khách hàng trong diện
     * là bỏ sót — và bỏ sót thì không ai phát hiện ra.
     *
     * <h2>Vì sao trạng thái chấp nhận CẢ HAI bộ mã</h2>
     * Loại khách hàng nay dùng chung một bộ mã {@code I}/{@code O} ở cả Core thật lẫn Core giả,
     * nên chỉ cần hỏi cấu hình. Trạng thái thì chưa: Core thật dùng một ký tự (A, C, L, P, X)
     * còn Core giả vẫn phát {@code ACTIVE}/{@code APPROVED}. Nhận cả hai là cách giữ nguyên tắc
     * "nghi ngờ thì vẫn quét" trong quãng giao thời — chọn một bộ và loại bộ kia sẽ tạo ra
     * false negative ở đúng cái đường sinh ra để bắt thay đổi tức thời.
     * <p>
     * Khi Core thật bắt đầu phát sự kiện, bỏ nhánh mã của Core giả đi.
     */
    public boolean isScanTarget(CustomerChangedEvent e) {
        boolean individual = e.getCustomerType() == null
                || customerServiceImpl.isIndividualType(e.getCustomerType());
        boolean active = e.getStatus() == null
                || MOCK_CORE_SCAN_TARGET_STATUSES.contains(e.getStatus())
                || customerServiceImpl.isScanTargetStatus(e.getStatus());
        return individual && active;
    }

    /**
     * Ghi một trang của job đồng bộ.
     *
     * <h2>Vì sao một SELECT cho cả trang chứ không phải mỗi dòng một lần</h2>
     * {@code ON CONFLICT} không cần đọc trước; JPA thì cần, vì nó phải biết dòng đã có hay
     * chưa để chọn INSERT hay UPDATE. Gọi {@code findById} cho từng dòng là 2000 lượt đi về
     * cho mỗi trang, tức 5 triệu lượt cho một lần nạp đầu — đó mới là điều biến việc đổi sang
     * JPA thành một job chạy qua đêm. Đọc cả trang bằng một câu {@code IN} thì mỗi trang chỉ
     * thêm đúng một lượt.
     *
     * <h2>Vì sao flush rồi clear</h2>
     * Không clear thì persistence context giữ lại {@link CustomerIdentity} của mọi trang đã
     * chạy — tới cuối một lần nạp đầu là 5 triệu entity trong heap, và dirty-checking ở mỗi
     * lần flush phải duyệt lại tất cả, nên trang sau luôn chậm hơn trang trước. Clear theo
     * trang giữ chi phí đó phẳng.
     *
     * @return số dòng bị chốt thứ tự bỏ qua vì bản chiếu đang giữ dữ liệu mới hơn Core
     */
    private int upsertPage(List<CoreCustomer> page) {
        return pageTransaction.execute(status -> writePage(page));
    }

    private int writePage(List<CoreCustomer> page) {
        Map<String, CustomerIdentity> existing = identityRepository
                .findAllByCifInForUpdate(page.stream().map(CoreCustomer::getCif).toList())
                .stream()
                .collect(Collectors.toMap(CustomerIdentity::getCif, Function.identity()));

        int stale = 0;
        for (CoreCustomer c : page) {
            MirrorWrite w = MirrorWrite.builder()
                    .cif(c.getCif())
                    // KHÔNG dùng isScanTarget(CustomerChangedEvent): đó là quy tắc của đường
                    // sự kiện, nơi thiếu trường thì hiểu theo hướng vẫn quét. Ở đây dữ liệu đọc
                    // thẳng từ Core nên không trường nào thiếu, và tập trạng thái cần quét là
                    // tham số cấu hình — CoreCustomerReader giữ định nghĩa duy nhất của nó.
                    .scanTarget(customerServiceImpl.isScanTarget(c))
                    .fullName(c.getFullName())
                    .dob(c.getDob())
                    .phone(c.getPhone())
                    .idNumber(c.getIdNumber())
                    .oldIdNumber(c.getOldIdNumber())
                    .occurredAt(c.getCoreUpdatedAt())
                    .build();

            Instant mark = clampToNow(w.occurredAt());
            CustomerIdentity row = existing.get(c.getCif());
            if (row == null) {
                row = CustomerIdentity.builder().cif(c.getCif()).build();
                applyTo(row, w, mark);
                // persist chứ không save: đã biết chắc dòng chưa có, còn save() sẽ merge và
                // bắn thêm một SELECT cho mỗi dòng mới — đúng thứ vừa gộp đi ở trên.
                entityManager.persist(row);
            } else if (row.getCoreUpdatedAt().isAfter(mark)) {
                stale++;
            } else {
                applyTo(row, w, mark);
            }
        }

        entityManager.flush();
        entityManager.clear();
        return stale;
    }

    private Instant readWatermark() {
        String raw = configService.get(KEY_WATERMARK, null);
        if (raw == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Sync watermark '{}' is unreadable — restarting from scratch so nothing is missed", raw);
            return Instant.EPOCH;
        }
    }

    @lombok.Builder
    @lombok.Getter
    public static class SyncResult {
        private final String mode;
        private final int rowsSynced;
        private final long scanTargetsInMirror;
        private final long elapsedMillis;
    }
}
