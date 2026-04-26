package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.response.AuditLogResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;

import java.time.LocalDateTime;

public interface AuditService {

    void log(Long userId, Long walletId, String permissionCode,
             String targetId, boolean granted, String denialReason,
             String ipAddress, String userAgent);

    PageResponse<AuditLogResponse> getLogsByUser(Long userId, int page, int size);

    PageResponse<AuditLogResponse> getLogsByWallet(Long walletId, int page, int size);

    PageResponse<AuditLogResponse> getDeniedAccess(
            Long walletId, LocalDateTime from, LocalDateTime to, int page, int size
    );
}