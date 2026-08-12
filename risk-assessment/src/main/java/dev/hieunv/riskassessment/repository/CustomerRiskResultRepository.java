package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.constant.CoreSendStatus;
import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.dto.screening.RiskHistoryRow;
import dev.hieunv.riskassessment.dto.screening.ScreeningResultRow;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface CustomerRiskResultRepository extends JpaRepository<CustomerRiskResult, Long> {

    @Modifying
    @Query(value = "UPDATE CustomerRiskResult c SET c.latest = FALSE WHERE c.cif = :cif AND c.latest = TRUE ")
    void clearLatestFlag(@Param("cif") String cif);

    Optional<CustomerRiskResult> findByCifAndLatestTrue(String cif);

    @Query("""
            SELECT r.cif FROM CustomerRiskResult r
            WHERE r.latest = TRUE AND r.riskScore = :score AND r.cif IN :cifs
            """)
    Set<String> findCifsWithLatestScore(@Param("cifs") Collection<String> cifs,
                                        @Param("score") short score);

    List<CustomerRiskResult> findByScanBatchIdOrderByIdAsc(UUID scanBatchId);

    /**
     * Báo cáo "Kết quả screening".
     *
     * <h2>Vì sao {@code r.latest = TRUE} là toàn bộ luật "chỉ lấy lần đánh giá mới nhất"</h2>
     * Không cần {@code GROUP BY cif} hay window function: mỗi CIF có đúng một dòng mang cờ
     * này, và điều đó do partial unique index {@code uq_result_latest_per_cif} bảo đảm ở tầng
     * DB. Viết lại luật bằng {@code max(created_at)} trong câu truy vấn là dựng một định nghĩa
     * thứ hai cho cùng một khái niệm — hai định nghĩa thì sớm muộn lệch nhau.
     *
     * <h2>Vì sao LEFT JOIN chứ không JOIN</h2>
     * Bản chiếu là bảng khác, đồng bộ theo đường khác. Một CIF vừa bị đánh giá rủi ro mà chưa
     * kịp có dòng trong bản chiếu vẫn <b>phải</b> xuất hiện trong báo cáo — INNER JOIN sẽ làm
     * nó biến mất, và thứ biến mất khỏi báo cáo AML thì không ai đi tìm.
     *
     * <h2>Vì sao mỗi bộ lọc đi kèm một cờ "any"</h2>
     * Tham số rỗng nghĩa là "Tất cả". Dựng câu SQL động theo từng tổ hợp bộ lọc thì có 32 câu
     * khác nhau và không câu nào được kiểm chứng; một câu duy nhất với cờ bật/tắt thì chỉ có
     * một kế hoạch thực thi để nhìn. {@code riskScores} vẫn phải truyền danh sách khác rỗng
     * khi {@code anyScore = TRUE} — cùng lý do với {@code UNUSED_STATUS_LIST} bên
     * {@code CoreCustomerServiceImpl}: {@code IN ()} không phải SQL hợp lệ.
     */
    @Query(value = """
            SELECT new dev.hieunv.riskassessment.dto.screening.ScreeningResultRow(
                       r.cif, i.fullName, i.idNumber,
                       r.riskLevel, r.riskScore, r.reason, r.createdAt)
            FROM CustomerRiskResult r
            LEFT JOIN CustomerIdentity i ON i.cif = r.cif
            WHERE r.latest = TRUE
              AND (:anyLevel = TRUE OR r.riskLevel = :riskLevel)
              AND (:anyScore = TRUE OR r.riskScore IN :riskScores)
              AND (:anyReason = TRUE OR r.categoryCode = :reason)
              AND (:phone IS NULL OR i.phoneNorm LIKE :phone)
              AND (:idNumber IS NULL OR i.idNumberNorm LIKE :idNumber)
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = """
                    SELECT count(r)
                    FROM CustomerRiskResult r
                    LEFT JOIN CustomerIdentity i ON i.cif = r.cif
                    WHERE r.latest = TRUE
                      AND (:anyLevel = TRUE OR r.riskLevel = :riskLevel)
                      AND (:anyScore = TRUE OR r.riskScore IN :riskScores)
                      AND (:anyReason = TRUE OR r.categoryCode = :reason)
                      AND (:phone IS NULL OR i.phoneNorm LIKE :phone)
                      AND (:idNumber IS NULL OR i.idNumberNorm LIKE :idNumber)
                    """)
    Page<ScreeningResultRow> searchLatestResults(@Param("anyLevel") boolean anyLevel,
                                                 @Param("riskLevel") RiskLevel riskLevel,
                                                 @Param("anyScore") boolean anyScore,
                                                 @Param("riskScores") Collection<Short> riskScores,
                                                 @Param("anyReason") boolean anyReason,
                                                 @Param("reason") String reason,
                                                 @Param("phone") String phone,
                                                 @Param("idNumber") String idNumber,
                                                 Pageable pageable);

    /**
     * Toàn bộ lịch sử đánh giá của một khách hàng — không lọc {@code latest}.
     * <p>
     * Sắp thêm theo {@code id} vì {@code created_at} là {@code TIMESTAMPTZ} và hai lượt đánh
     * giá trong cùng một batch có thể trùng mốc tới từng micro giây. Thiếu khóa phụ thì thứ tự
     * giữa các dòng trùng mốc không xác định, và phân trang sẽ lặp hoặc bỏ sót dòng.
     */
    @Query(value = """
            SELECT new dev.hieunv.riskassessment.dto.screening.RiskHistoryRow(
                       r.createdAt, r.riskLevel, r.riskScore, r.reason)
            FROM CustomerRiskResult r
            WHERE r.cif = :cif
            ORDER BY r.createdAt DESC, r.id DESC
            """,
            countQuery = "SELECT count(r) FROM CustomerRiskResult r WHERE r.cif = :cif")
    Page<RiskHistoryRow> findHistoryByCif(@Param("cif") String cif, Pageable pageable);

    long countByScanBatchId(UUID scanBatchId);


    @Query(value = """
            SELECT * FROM customer_risk_result
            WHERE core_send_status IN ('PENDING', 'FAILED')
              AND (next_attempt_at IS NULL OR next_attempt_at <= now())
              AND attempt_count < :maxAttempts
            ORDER BY next_attempt_at NULLS FIRST, id
            LIMIT :limit
            """, nativeQuery = true)
    List<CustomerRiskResult> findDueForDispatch(@Param("maxAttempts") int maxAttempts,
                                                @Param("limit") int limit);

    long countByCoreSendStatus(CoreSendStatus status);

    /** Đã hết lượt tự gửi — cần người nhìn tới. */
    @Query(value = """
            SELECT count(*) FROM customer_risk_result
            WHERE core_send_status = 'FAILED' AND attempt_count >= :maxAttempts
            """, nativeQuery = true)
    long countExhausted(@Param("maxAttempts") int maxAttempts);


    @Modifying
    @Query(value = """
            UPDATE customer_risk_result
            SET core_send_status = 'PENDING',
                attempt_count = 0,
                next_attempt_at = NULL,
                last_error = NULL
            WHERE core_send_status = 'FAILED'
            """, nativeQuery = true)
    int requeueFailed();
}
