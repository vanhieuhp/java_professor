package dev.hieunv.riskassessment.entity;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.constant.RiskLevel;
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

@Entity
@Table(name = "watchlist_category")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    /** 0 = DS đen; 1..9 = mức ưu tiên của DS mẫu theo bảng A.6. Duyệt tăng dần. */
    @Column(name = "priority", nullable = false)
    private Short priority;

    @Column(name = "sub_order", nullable = false)
    @Builder.Default
    private Short subOrder = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 2)
    private MatchType matchType;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false, length = 20)
    private RiskLevel riskLevel;

    @Column(name = "risk_score", nullable = false)
    private Short riskScore;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "is_blacklist", nullable = false)
    @Builder.Default
    private boolean blacklist = false;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "entries_changed_at")
    private Instant entriesChangedAt;
}
