package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.entity.CoreCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Đọc DB Core ví — bước B1 của cả TH1 và TH3.
 *
 * <h2>Vì sao keyset pagination, không phải OFFSET</h2>
 * {@code OFFSET 4000000 LIMIT 1000} buộc Postgres đọc và bỏ đi 4 triệu dòng trước khi trả
 * về 1000 dòng cần lấy. Chi phí tăng tuyến tính theo số trang, nên trang cuối của một lần
 * quét 5 triệu khách hàng đắt gấp hàng nghìn lần trang đầu.
 * <p>
 * {@code WHERE id > :afterId ORDER BY id LIMIT n} thì mọi trang đều rẻ như nhau: index
 * nhảy thẳng tới vị trí cần, đọc đúng n dòng. Thêm một lợi ích quan trọng hơn: con trỏ là
 * một giá trị id có thể lưu lại được, nên job chết giữa chừng thì chạy lại từ đúng chỗ đó —
 * còn OFFSET thì trang thứ N sẽ trượt nếu dữ liệu nguồn thay đổi.
 */
public interface CoreCustomerRepository extends JpaRepository<CoreCustomer, Long> {

    /**
     * TH1 và TH3a — toàn bộ khách hàng CÁ NHÂN trạng thái Active/Approved.
     * Khớp đúng vị từ của partial index {@code idx_core_scan_target}.
     */
    @Query(value = """
            SELECT * FROM core.wallet_customer
            WHERE customer_type = 'CN'
              AND status IN ('ACTIVE', 'APPROVED')
              AND id > :afterId
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<CoreCustomer> findScanTargetsAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * TH3b — chỉ khách hàng phát sinh trong ngày liền trước, và <b>điểm khác 7</b>.
     * <p>
     * Điều kiện điểm ≠ 7 (Q4): khách hàng đã bị DS đen bắt và khóa CIF thì không cần chấm
     * lại bằng DS mẫu — điểm 7 đã là mức cao nhất, không danh sách nào nâng thêm được.
     * {@code risk_score IS NULL} vẫn được lấy: chưa từng đánh giá thì phải đánh giá.
     */
    @Query(value = """
            SELECT * FROM core.wallet_customer
            WHERE customer_type = 'CN'
              AND status IN ('ACTIVE', 'APPROVED')
              AND (risk_score IS NULL OR risk_score <> 7)
              AND (created_at >= :from AND created_at < :to
                   OR update_time >= :from AND update_time < :to)
              AND id > :afterId
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<CoreCustomer> findChangedScanTargetsAfter(@Param("afterId") long afterId,
                                                   @Param("from") Instant from,
                                                   @Param("to") Instant to,
                                                   @Param("limit") int limit);

    @Query(value = """
            SELECT count(*) FROM core.wallet_customer
            WHERE customer_type = 'CN' AND status IN ('ACTIVE', 'APPROVED')
            """, nativeQuery = true)
    long countScanTargets();

    /**
     * Đồng bộ bản chiếu định danh — lấy <b>TẤT CẢ</b> khách hàng, không lọc trạng thái.
     * <p>
     * Cố ý không dùng {@link #findScanTargetsAfter}: bản chiếu phải biết cả những người
     * <i>không còn</i> thuộc tập quét. Nếu chỉ nạp KH Active thì một người bị khóa ví sẽ
     * nằm lại vĩnh viễn trong bản chiếu với {@code scan_target = true} cũ, và tiếp tục bị
     * quét ngược bắt — bản chiếu chỉ có đường vào mà không có đường ra.
     */
    @Query(value = """
            SELECT * FROM core.wallet_customer
            WHERE id > :afterId
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<CoreCustomer> findAllAfter(@Param("afterId") long afterId, @Param("limit") int limit);

    /**
     * Đọc đúng những khách hàng mà quét ngược đã chỉ ra — bước B2 của T1R.
     * <p>
     * Bản chiếu {@code pcrt_customer_identity} chỉ giữ 4 trường định danh đã chuẩn hóa, nên
     * vẫn phải quay lại Core để lấy dữ liệu đầy đủ (bản thô để ghi biên bản, quốc tịch /
     * nghề nghiệp / chức vụ cho K2-K4, điểm rủi ro hiện tại). Khác biệt so với chiều xuôi
     * không nằm ở chỗ "có gọi Core hay không" mà ở chỗ <b>gọi cho bao nhiêu người</b>:
     * vài chục thay vì 5 triệu.
     */
    @Query(value = """
            SELECT * FROM core.wallet_customer
            WHERE cif IN (:cifs)
            ORDER BY id
            """, nativeQuery = true)
    List<CoreCustomer> findByCifIn(@Param("cifs") Collection<String> cifs);

    /** Đồng bộ delta: chỉ các dòng Core đã đổi sau mốc lần đồng bộ trước. */
    @Query(value = """
            SELECT * FROM core.wallet_customer
            WHERE update_time > :since AND id > :afterId
            ORDER BY id
            LIMIT :limit
            """, nativeQuery = true)
    List<CoreCustomer> findChangedAfter(@Param("since") Instant since,
                                        @Param("afterId") long afterId,
                                        @Param("limit") int limit);
}
