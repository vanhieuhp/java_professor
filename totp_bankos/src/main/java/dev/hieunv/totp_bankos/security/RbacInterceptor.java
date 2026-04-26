package dev.hieunv.totp_bankos.security;

import dev.hieunv.totp_bankos.domain.AuditLog;
import dev.hieunv.totp_bankos.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class RbacInterceptor implements HandlerInterceptor {

    private final AuditLogRepository auditLogRepository;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        if (!(handler instanceof HandlerMethod method)) {
            return true; // not a controller method — pass through
        }

        RequiresPermission annotation = method.getMethodAnnotation(RequiresPermission.class);
        if (annotation == null) {
            return true; // no permission required — pass through
        }

        String  requiredPermission = annotation.value();
        Long    userId             = AppSecurityContext.getUserId();
        Long    walletId           = AppSecurityContext.getWalletId();
        boolean granted            = AppSecurityContext.hasPermission(requiredPermission);

        // write audit record (fire-and-forget — no tx boundary here)
        writeAuditLog(userId, walletId, requiredPermission, granted,
                request.getRemoteAddr(), request.getHeader("User-Agent"));

        if (!granted) {
            log.warn("Access denied: userId={} walletId={} permission={}",
                    userId, walletId, requiredPermission);
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"success\":false,\"error\":\"Access denied: missing permission " +
                            requiredPermission + "\"}"
            );
            return false;
        }

        return true;
    }

    // ── private ───────────────────────────────────────────────

    private void writeAuditLog(Long userId, Long walletId, String permissionCode,
                               boolean granted, String ipAddress, String userAgent) {
        try {
            String[] parts       = permissionCode != null ? permissionCode.split(":") : new String[]{};
            String featureCode   = parts.length > 0 ? parts[0] : null;
            String functionCode  = parts.length > 1 ? parts[1] : null;

            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .walletId(walletId)
                    .featureCode(featureCode)
                    .functionCode(functionCode)
                    .permissionCode(permissionCode)
                    .granted(granted)
                    .denialReason(granted ? null : "Permission not assigned to user's group")
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .createdAt(LocalDateTime.now())
                    .build();

            auditLogRepository.save(log);
        } catch (Exception e) {
            // never let audit failure block the request
            log.warn("Failed to write audit log: {}", e.getMessage());
        }
    }
}