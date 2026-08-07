package dev.hieunv.riskassessment.entity;

import dev.hieunv.riskassessment.constant.CoreSendStatus;
import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.constant.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Kết quả đánh giá rủi ro của một khách hàng tại một thời điểm.
 *
 * <h2>Q5 — ghi đè hay append lịch sử</h2>
 * Chốt là <b>append</b>. Đây là hệ thống bị thanh tra: phải trả lời được "ngày X hệ thống
 * đánh giá khách hàng này ra sao và dựa trên bản ghi nào", nên kết quả cũ không được biến
 * mất. Bản mới nhất mang cờ {@code isLatest}, và unique index partial
 * {@code uq_result_latest_per_cif} bảo đảm mỗi CIF chỉ có đúng MỘT bản ghi như vậy — ràng
 * buộc nằm ở DB chứ không trông vào code nhớ tắt cờ cũ.
 * <p>
 * Đây là một giả định, chưa phải yêu cầu đã chốt với BA.
 */
@Entity
@Table(name = "customer_risk_result")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRiskResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "cif", nullable = false, length = 50)
    private String cif;

    @Column(name = "scan_batch_id", nullable = false)
    private UUID scanBatchId;

    @Column(name = "scan_queue_id", nullable = false)
    private Long scanQueueId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 4)
    private TriggerType triggerType;

    // ----- Contract gửi Core (A.4-B3) -----

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score", nullable = false)
    private Short riskScore;

    @Column(name = "reason", nullable = false)
    private String reason;

    // ----- Vết truy xuất -----

    @Column(name = "category_code", nullable = false, length = 50)
    private String categoryCode;

    @Column(name = "entry_id")
    private Long entryId;

    @Column(name = "matched_fields", length = 200)
    private String matchedFields;

    /** Chỉ true khi trùng DS đen — Core phải cập nhật điểm VÀ chạy quy trình khóa CIF. */
    @Column(name = "lock_cif_required", nullable = false)
    @Builder.Default
    private boolean lockCifRequired = false;

    @Column(name = "is_latest", nullable = false)
    @Builder.Default
    private boolean latest = true;

    // ----- Gửi Core (Phase 5) -----

    @Enumerated(EnumType.STRING)
    @Column(name = "core_send_status", nullable = false, length = 20)
    @Builder.Default
    private CoreSendStatus coreSendStatus = CoreSendStatus.PENDING;

    @Column(name = "core_sent_at")
    private Instant coreSentAt;

    @Column(name = "attempt_count", nullable = false)
    @Builder.Default
    private Short attemptCount = 0;

    /**
     * Thời điểm sớm nhất được thử gửi lại — backoff giữa các LẦN CHẠY JOB.
     * Khác với retry bên trong một lần gọi: retry xử lý trục trặc mạng thoáng qua tính bằng
     * mili-giây, còn cột này xử lý việc Core đang thực sự down và cần vài phút để hồi phục.
     */
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
