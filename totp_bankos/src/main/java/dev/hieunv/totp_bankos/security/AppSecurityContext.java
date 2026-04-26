package dev.hieunv.totp_bankos.security;

import java.util.Collections;
import java.util.List;

/**
 * Thread-local security context populated by filters on every request.
 * Controllers and services call static getters — no Spring injection needed.
 */
public final class AppSecurityContext {

    private static final ThreadLocal<Long>         USER_ID     = new ThreadLocal<>();
    private static final ThreadLocal<Long>         WALLET_ID   = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();

    private AppSecurityContext() {}

    // ── writers (called by JwtAuthFilter) ─────────────────────

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static void setWalletId(Long walletId) {
        WALLET_ID.set(walletId);
    }

    public static void setPermissions(List<String> permissions) {
        PERMISSIONS.set(permissions != null ? permissions : Collections.emptyList());
    }

    // ── readers (called by services / interceptor) ─────────────

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long getWalletId() {
        return WALLET_ID.get();
    }

    public static List<String> getPermissions() {
        List<String> p = PERMISSIONS.get();
        return p != null ? p : Collections.emptyList();
    }

    public static boolean hasPermission(String code) {
        return getPermissions().contains(code);
    }

    // ── cleanup (called by filter in finally block) ────────────

    public static void clear() {
        USER_ID.remove();
        WALLET_ID.remove();
        PERMISSIONS.remove();
    }
}