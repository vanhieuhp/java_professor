package dev.hieunv.totp_bankos.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "wallet_id")
    private Long walletId;

    @Column(name = "feature_code", length = 50)
    private String featureCode;

    @Column(name = "function_code", length = 50)
    private String functionCode;

    @Column(name = "permission_code", length = 100)
    private String permissionCode;

    @Column(name = "target_id")
    private String targetId;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "denial_reason")
    private String denialReason;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
}