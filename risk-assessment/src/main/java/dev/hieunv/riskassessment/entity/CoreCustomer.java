package dev.hieunv.riskassessment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Khách hàng bên Core ví — PCRT chỉ ĐỌC, không bao giờ ghi.
 * <p>
 * Trong hệ thống thật đây là database của một hệ thống khác. Ở project học này nó nằm ở
 * schema {@code core} của cùng một Postgres, nhưng ranh giới vẫn phải được tôn trọng:
 * {@link Immutable} khiến Hibernate không sinh câu UPDATE nào cho bảng này, kể cả khi
 * ai đó lỡ setter rồi flush.
 */
@Entity
@Immutable
@Table(name = "wallet_customer", schema = "core")
@Getter
@Setter
@ToString
@NoArgsConstructor
public class CoreCustomer {

    @Id
    private Long id;

    @Column(name = "cif", nullable = false, length = 50)
    private String cif;

    /** CN = cá nhân, TC = tổ chức. Spec chỉ quét khách hàng cá nhân. */
    @Column(name = "customer_type", nullable = false, length = 2)
    private String customerType;

    /** ACTIVE | APPROVED | LOCKED | CLOSED. Spec chỉ quét Active/Approved. */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "dob")
    private LocalDate dob;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "old_id_number", length = 50)
    private String oldIdNumber;

    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "occupation_code", length = 50)
    private String occupationCode;

    @Column(name = "position_code", length = 50)
    private String positionCode;

    /** Điểm rủi ro Core đang giữ — TH3b loại các KH đã có điểm 7. */
    @Column(name = "risk_score")
    private Short riskScore;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "update_time", nullable = false)
    private Instant updateTime;
}
