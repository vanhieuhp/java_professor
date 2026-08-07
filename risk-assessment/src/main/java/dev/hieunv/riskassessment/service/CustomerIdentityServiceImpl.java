package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.UpsertCustomerRequest;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.entity.CustomerIdentity;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import dev.hieunv.riskassessment.mapper.CustomerMapper;
import dev.hieunv.riskassessment.utils.Normalizer;
import dev.hieunv.riskassessment.repository.CoreCustomerRepository;
import dev.hieunv.riskassessment.repository.CustomerIdentityRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerIdentityServiceImpl implements CustomerIdentityService {

    private static final Set<String> SCAN_TARGET_STATUSES = Set.of("ACTIVE", "APPROVED");

    /**
     * Phiên bản quy tắc chuẩn hóa đang dùng — đổi {@link Normalizer} thì tăng số này.
     */
    private static final short NORMALIZER_VERSION = 1;

    private final CoreCustomerRepository coreCustomerRepository;
    private final CustomerIdentityRepository identityRepository;
    private final PcrtConfigService configService;
    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Ranh giới transaction của job đồng bộ: <b>một trang, một transaction</b>.
     *
     * <h2>Vì sao là TransactionTemplate chứ không phải {@code @Transactional}</h2>
     * Hai lý do, mỗi lý do đều đủ:
     * <ul>
     *   <li>{@link #upsertPage} được gọi từ {@link #run} trong cùng một đối tượng. Proxy của
     *       Spring không chặn được lời gọi nội bộ, nên annotation trên method private đó sẽ
     *       <b>im lặng không có tác dụng</b> — code trông như có transaction mà không có.</li>
     *   <li>Đặt annotation lên {@code run} thì 5 triệu dòng nằm trong một transaction: một
     *       snapshot giữ suốt nhiều phút, undo log phình ra, và một lỗi ở trang cuối cuốn theo
     *       toàn bộ công đã làm. Một transaction mỗi trang giữ đúng hành vi cũ của
     *       {@code batchUpdate}.</li>
     * </ul>
     */
    private TransactionTemplate pageTransaction;

    @PostConstruct
    void initPageTransaction() {
        pageTransaction = new TransactionTemplate(transactionManager);
    }

    public static final String KEY_WATERMARK = "identity.sync.watermark";

    /**
     * Nạp lần đầu: toàn bộ Core. Chạy một lần, sau đó chỉ chạy {@link #syncDelta()}.
     */
    public SyncResult fullSync() {
        int pageSize = configService.getInt("identity.sync.page.size", 2000);
        return run("toàn bộ", pageSize,
                afterId -> coreCustomerRepository.findAllAfter(afterId, pageSize));
    }

    /**
     * Đồng bộ delta theo {@code update_time} của Core.
     *
     * <h2>Mốc lấy từ đâu</h2>
     * Từ {@code pcrt_config}, nơi chỉ job này ghi. <b>Không</b> lấy bằng
     * {@code max(core_updated_at)} của bảng chiếu: từ khi TH2 được phép ghi vào bảng đó, giá
     * trị ấy do một hệ thống khác quyết định, và chỉ cần một sự kiện mang mốc tương lai là
     * mốc đồng bộ vượt qua hiện tại rồi không bao giờ khớp dòng nào nữa. Job vẫn chạy, vẫn
     * báo thành công, vẫn ghi "0 dòng" — bản chiếu đứng yên và không ai biết.
     *
     * <h2>Vì sao lùi lại 1 phút</h2>
     * Dùng {@code >} thì bỏ sót dòng có mốc đúng bằng mốc cũ; dùng {@code >=} thì lặp lại.
     * Bỏ sót trong AML nặng hơn nhiều so với xử lý lại, mà upsert vốn idempotent — nên chọn
     * chồng lấn.
     */
    @Override
    public SyncResult syncDelta() {
        int pageSize = configService.getInt("identity.sync.page.size", 2000);
        Instant since = readWatermark().minusSeconds(60);
        log.info("Đồng bộ định danh delta từ mốc {}", since);
        return run("delta", pageSize,
                afterId -> coreCustomerRepository.findChangedAfter(since, afterId, pageSize));
    }

    private SyncResult run(String mode, int pageSize, LongFunction<List<CoreCustomer>> pageReader) {
        long startedNanos = System.nanoTime();

        // Chốt mốc mới TRƯỚC khi đọc, và ghi lại sau khi xong. Lấy mốc lúc kết thúc sẽ bỏ sót
        // mọi thay đổi phát sinh trong lúc lượt này đang chạy — với 5 triệu dòng thì đó là
        // vài phút thay đổi biến mất không dấu vết.
        Instant runStartedAt = dbNow();
        long cursor = 0L;
        int total = 0;
        int stale = 0;

        while (true) {
            List<CoreCustomer> page = pageReader.apply(cursor);
            if (page.isEmpty()) {
                break;
            }
            stale += upsertPage(page);
            total += page.size();
            cursor = page.get(page.size() - 1).getId();
        }

        // Job đọc Core rồi ghi bản chiếu; giữa hai việc đó đường realtime ghi xen vào được.
        // Số này là số lần điều đó thật sự xảy ra — bằng 0 thì hai đường không giẫm chân nhau,
        // khác 0 thì chốt thứ tự vừa làm đúng việc của nó chứ không phải có gì hỏng.
        if (stale > 0) {
            log.info("Đồng bộ định danh ({}): {} dòng bị chốt thứ tự bỏ qua vì bản chiếu "
                    + "đang giữ dữ liệu mới hơn Core", mode, stale);
        }

        // Chỉ dời mốc khi đã chạy hết. Ném ngoại lệ giữa chừng thì mốc giữ nguyên và lượt sau
        // làm lại từ đầu — chậm, nhưng không bỏ sót.
        configService.set(KEY_WATERMARK, runStartedAt.toString());

        long millis = (System.nanoTime() - startedNanos) / 1_000_000;
        long scanTargets = identityRepository.countScanTargets();
        log.info("Đồng bộ định danh ({}) xong: {} dòng trong {} ms — bản chiếu có {} KH thuộc tập quét",
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
        // COALESCE: bên gọi không biết core_id thì giữ nguyên giá trị job đồng bộ đã điền.
        // Gán thẳng sẽ xóa nó mỗi lần TH2 ghi đè.
        if (w.coreId() != null) {
            row.setCoreId(w.coreId());
        }

        row.setScanTarget(w.scanTarget());
        row.setFullNameNorm(Normalizer.name(w.fullName()));
        row.setDob(w.dob());
        row.setPhoneNorm(Normalizer.phone(w.phone()));
        row.setIdNumberNorm(Normalizer.idNumber(w.idNumber()));
        row.setOldIdNumberNorm(Normalizer.idNumber(w.oldIdNumber()));
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
                               Long coreId,
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
            log.warn("CIF {} — {} {} nằm ở tương lai, sẽ bị cắt về hiện tại. "
                    + "Kiểm tra đồng hồ/múi giờ của {}.", cif, field, mark, source);
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
            log.warn("CIF {} — Core báo xóa nhưng bản chiếu không có dòng nào", cif);
            return false;
        }
        if (row.getDeletedAt() != null) {
            log.info("CIF {} — đã gỡ khỏi bản chiếu từ {}, bỏ qua sự kiện DELETED lặp",
                    cif, row.getDeletedAt());
            return false;
        }
        if (row.getCoreUpdatedAt().isAfter(mark)) {
            log.warn("CIF {} — bỏ qua gỡ khỏi bản chiếu: đang giữ dữ liệu mới hơn mốc {} "
                    + "của sự kiện DELETED này", cif, occurredAt);
            return false;
        }

        Instant now = Instant.now();
        row.setScanTarget(false);
        row.setDeletedAt(now);
        row.setCoreUpdatedAt(mark);
        row.setSyncedAt(now);
        identityRepository.save(row);

        log.warn("CIF {} đã được gỡ khỏi tập quét theo sự kiện DELETED của Core", cif);
        return true;
    }

    /**
     * Đúng tập mà TH1/TH3a quét: khách hàng <b>cá nhân</b>, ví đang hoạt động.
     * <p>
     * Thiếu trường thì hiểu theo hướng <b>vẫn quét</b>. Đây là lựa chọn có chủ ý: rà soát
     * nhầm một khách hàng ngoài diện chỉ tốn công, còn loại nhầm một khách hàng trong diện
     * là bỏ sót — và bỏ sót thì không ai phát hiện ra.
     */
    public static boolean isScanTarget(CustomerChangedEvent e) {
        boolean individual = e.getCustomerType() == null || "CN".equals(e.getCustomerType());
        boolean active = e.getStatus() == null || SCAN_TARGET_STATUSES.contains(e.getStatus());
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
                    .coreId(c.getId())
                    // KHÔNG dùng isScanTarget: đó là quy tắc của đường sự kiện, nơi thiếu
                    // trường thì hiểu theo hướng vẫn quét. Ở đây dữ liệu đọc thẳng từ Core nên
                    // không có trường nào thiếu, và "null nghĩa là không phải KH cá nhân".
                    .scanTarget("CN".equals(c.getCustomerType())
                            && SCAN_TARGET_STATUSES.contains(c.getStatus()))
                    .fullName(c.getFullName())
                    .dob(c.getDob())
                    .phone(c.getPhone())
                    .idNumber(c.getIdNumber())
                    .oldIdNumber(c.getOldIdNumber())
                    .occurredAt(c.getUpdateTime())
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

    /**
     * Giờ của DATABASE. Mốc đồng bộ so với {@code update_time} do Postgres sinh — phải cùng đồng hồ.
     */
    private Instant dbNow() {
        return jdbcTemplate.queryForObject("SELECT now()", Timestamp.class).toInstant();
    }

    private Instant readWatermark() {
        String raw = configService.get(KEY_WATERMARK, null);
        if (raw == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException e) {
            log.error("Mốc đồng bộ '{}' không đọc được — chạy lại từ đầu để chắc chắn không bỏ sót", raw);
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
