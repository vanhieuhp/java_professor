package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.AuditLog;
import dev.hieunv.totp_bankos.dto.response.AuditLogResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.mapper.AuditLogMapper;
import dev.hieunv.totp_bankos.repository.AuditLogRepository;
import dev.hieunv.totp_bankos.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuditServiceImpl implements AuditService {

    private final AuditLogRepository auditLogRepository;
    private final AuditLogMapper     auditLogMapper;

    @Override
    public void log(Long userId, Long walletId, String permissionCode,
                    String targetId, boolean granted, String denialReason,
                    String ipAddress, String userAgent) {

        // parse feature and function from permission code e.g. "TRANSFER:APPROVE"
        String[] parts       = permissionCode != null ? permissionCode.split(":") : new String[]{};
        String featureCode   = parts.length > 0 ? parts[0] : null;
        String functionCode  = parts.length > 1 ? parts[1] : null;

        AuditLog log = AuditLog.builder()
                .userId(userId)
                .walletId(walletId)
                .featureCode(featureCode)
                .functionCode(functionCode)
                .permissionCode(permissionCode)
                .targetId(targetId)
                .granted(granted)
                .denialReason(denialReason)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .createdAt(LocalDateTime.now())
                .build();

        auditLogRepository.save(log);
    }

    @Override
    public PageResponse<AuditLogResponse> getLogsByUser(Long userId, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    @Override
    public PageResponse<AuditLogResponse> getLogsByWallet(Long walletId, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findByWalletIdOrderByCreatedAtDesc(walletId, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    @Override
    public PageResponse<AuditLogResponse> getDeniedAccess(
            Long walletId, LocalDateTime from, LocalDateTime to, int page, int size) {
        Page<AuditLog> result = auditLogRepository
                .findDeniedByWalletIdAndTimeRange(walletId, from, to, PageRequest.of(page, size));
        return toPageResponse(result);
    }

    // ── private helper ──────────────────────────────────────────

    private PageResponse<AuditLogResponse> toPageResponse(Page<AuditLog> page) {
        return PageResponse.<AuditLogResponse>builder()
                .content(auditLogMapper.toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}