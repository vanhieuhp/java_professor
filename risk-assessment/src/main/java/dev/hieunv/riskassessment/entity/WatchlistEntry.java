package dev.hieunv.riskassessment.entity;

import dev.hieunv.riskassessment.constant.MatchType;
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

@Entity
@Table(name = "watchlist_entry")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 2)
    private MatchType matchType;

    // ----- K1: định danh cá nhân -----

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "full_name_norm")
    private String fullNameNorm;

    @Column(name = "dob")
    private LocalDate dob;

    /** Dùng khi nguồn chỉ công bố năm sinh, không có ngày/tháng. */
    @Column(name = "dob_year")
    private Short dobYear;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "phone_norm", length = 15)
    private String phoneNorm;

    @Column(name = "id_number", length = 50)
    private String idNumber;

    @Column(name = "id_number_norm", length = 50)
    private String idNumberNorm;

    // ----- K2 / K3 / K4: thuộc tính đơn -----

    /** ISO 3166-1 alpha-2 — giải Q7. */
    @Column(name = "country_code", length = 2)
    private String countryCode;

    @Column(name = "occupation_code", length = 50)
    private String occupationCode;

    @Column(name = "position_code", length = 50)
    private String positionCode;

    // ----- Truy vết & vận hành -----

    /** Nguồn dữ liệu: UN_SC, FATF, FINCEN, EPAY_INTERNAL... */
    @Column(name = "source", length = 100)
    private String source;

    /** Mã bản ghi ở nguồn gốc — cần khi giải trình với cơ quan thanh tra. */
    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    /** Đổi quy tắc chuẩn hóa thì mọi dòng cũ sai → dùng cột này để backfill. */
    @Column(name = "normalizer_version", nullable = false)
    @Builder.Default
    private Short normalizerVersion = 1;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private Instant updatedAt;
}
