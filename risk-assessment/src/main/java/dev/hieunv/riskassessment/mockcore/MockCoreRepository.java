package dev.hieunv.riskassessment.mockcore;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Các câu lệnh của phía Core ví.
 * <p>
 * Nằm ở package {@code mockcore} chứ không phải {@code repository} là có chủ ý: PCRT chỉ
 * ĐỌC Core ví. Mọi thao tác ghi ở đây thuộc về phía Core, và trong hệ thống thật chúng nằm
 * trong service khác, codebase khác. Để chung thư mục repository sẽ làm mờ ranh giới đó, và
 * sớm muộn ai đó gọi nhầm từ code PCRT.
 */
public interface MockCoreRepository extends JpaRepository<CoreRiskUpdateLog, Long> {

    /**
     * Ghi nhận lệnh nếu chưa từng nhận.
     * <p>
     * {@code ON CONFLICT DO NOTHING} trả về số dòng đã chèn: <b>1</b> = lệnh mới, <b>0</b> =
     * đã nhận trước đó. Một câu lệnh nguyên tử vừa kiểm tra vừa ghi — không có khe hở giữa
     * hai bước cho request song song lọt qua.
     */
    @Modifying
    @Query(value = """
            INSERT INTO core.risk_update_log
                (idempotency_key, cif, risk_level, risk_score, reason, cif_locked)
            VALUES (:key, :cif, :riskLevel, :riskScore, :reason, :lockCif)
            ON CONFLICT (idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int recordIfNew(@Param("key") String key,
                    @Param("cif") String cif,
                    @Param("riskLevel") String riskLevel,
                    @Param("riskScore") short riskScore,
                    @Param("reason") String reason,
                    @Param("lockCif") boolean lockCif);

    /**
     * Core cập nhật kết quả đánh giá, và khóa CIF nếu được yêu cầu.
     * <p>
     * Một câu UPDATE cho cả hai việc, nên không tồn tại trạng thái trung gian "đã có điểm 7
     * nhưng chưa khóa ví".
     */
    @Modifying
    @Query(value = """
            UPDATE core.wallet_customer
            SET risk_score = :riskScore,
                status = CASE WHEN :lockCif THEN 'LOCKED' ELSE status END,
                update_time = now()
            WHERE cif = :cif
            """, nativeQuery = true)
    int applyRiskAssessment(@Param("cif") String cif,
                            @Param("riskScore") short riskScore,
                            @Param("lockCif") boolean lockCif);

    @Query(value = "SELECT count(*) FROM core.wallet_customer WHERE cif = :cif", nativeQuery = true)
    long countCustomer(@Param("cif") String cif);

    long countByIdempotencyKey(String idempotencyKey);
}
