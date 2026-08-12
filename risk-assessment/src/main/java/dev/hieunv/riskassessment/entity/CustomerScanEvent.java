package dev.hieunv.riskassessment.entity;

import dev.hieunv.riskassessment.constant.ScanStatus;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "customer_scan_queue")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerScanEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scan_batch_id", nullable = false)
    private UUID scanBatchId;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 4)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private ScanStatus status = ScanStatus.PENDING;

    @Column(name = "cif", nullable = false, length = 50)
    private String cif;

    /* K1 */
    // 320 chứ không phải 255: Core tách họ tên làm ba cột varchar(100), ghép lại có thể
    // vượt 255 — xem migration V14.
    @Column(name = "full_name", length = 320)
    private String fullName;

    @Column(name = "full_name_norm", length = 320)
    private String fullNameNorm;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "phone_norm", length = 15)
    private String phoneNorm;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "id_number_norm", length = 50)
    private String idNumberNorm;

    @Column(name = "old_id_number", length = 50)
    private String oldIdNumber;

    @Column(name = "old_id_number_norm", length = 50)
    private String oldIdNumberNorm;

    // ----- K2 / K3 / K4 -----
    @Column(name = "country_code", length = 2)
    private String countryCode;

    // Core trả về chữ tự do varchar(100) chứ không phải mã — xem V14.
    @Column(name = "occupation_code", length = 100)
    private String occupationCode;

    @Column(name = "position_code", length = 100)
    private String positionCode;

    /** Không còn được điền: Core thật không có cột điểm rủi ro nào. Xem {@code ScanEventMapper}. */
    @Column(name = "core_risk_score")
    private Short coreRiskScore;

    @Column(name = "enqueued_at", nullable = false, insertable = false, updatable = false)
    private Instant enqueuedAt;

    @Column(name = "processed_at")
    private Instant processedAt;
}
