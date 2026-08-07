package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.constant.CoreSendStatus;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CustomerRiskResultRepository extends JpaRepository<CustomerRiskResult, Long> {

    /**
     * Hạ cờ bản ghi cũ trước khi chèn bản ghi mới. Phải chạy trong CÙNG transaction với
     * lệnh chèn, nếu không unique index {@code uq_result_latest_per_cif} sẽ từ chối —
     * và đó là điều tốt: DB bắt lỗi thay vì để dữ liệu hỏng đi qua.
     */
    @Modifying
    @Query(value = "UPDATE customer_risk_result SET is_latest = FALSE WHERE cif = :cif AND is_latest",
            nativeQuery = true)
    int clearLatestFlag(@Param("cif") String cif);

    Optional<CustomerRiskResult> findByCifAndLatestTrue(String cif);

    List<CustomerRiskResult> findByScanBatchIdOrderByIdAsc(UUID scanBatchId);

    long countByScanBatchId(UUID scanBatchId);

    /**
     * Hàng đợi gửi Core — khớp đúng vị từ của partial index {@code idx_result_dispatch}.
     * <p>
     * {@code next_attempt_at IS NULL} là các kết quả vừa sinh, chưa từng gửi; chúng được ưu
     * tiên (NULLS FIRST) để kết quả mới không bị các bản ghi đang backoff chặn đường.
     */
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

    /**
     * Đưa các kết quả đã hết lượt trở lại hàng đợi.
     * <p>
     * Cần thiết vì phần lớn nguyên nhân hết lượt là <b>lỗi cấu hình</b> chứ không phải dữ
     * liệu hỏng: sai {@code core.base-url}, sai đường dẫn, Core đổi contract. Sửa cấu hình
     * xong mà không có nút này thì cách duy nhất lấy lại các kết quả đó là UPDATE tay trên
     * DB production — điều không ai nên phải làm.
     */
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
