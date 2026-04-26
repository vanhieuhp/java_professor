package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AuditLogResponse {

    private Long id;
    private Long userId;
    private Long walletId;
    private String featureCode;
    private String functionCode;
    private String permissionCode;
    private String targetId;
    private boolean granted;
    private String denialReason;
    private String ipAddress;
    private LocalDateTime createdAt;
}