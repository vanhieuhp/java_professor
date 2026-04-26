package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisServiceImpl implements RedisService {

    private static final String BLACKLIST_PREFIX    = "blacklist:token:";
    private static final String PERMISSION_PREFIX   = "permissions:user:%d:wallet:%d";

    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.rbac.permission-cache-ttl-minutes}")
    private long permissionCacheTtlMinutes;

    // ── token blacklist ───────────────────────────────────────

    @Override
    public void blacklistToken(String tokenId) {
        String key = BLACKLIST_PREFIX + tokenId;
        // keep the blacklist entry alive at least as long as the token could live
        long ttlSeconds = accessTokenExpiryMs / 1000;
        redisTemplate.opsForValue().set(key, "1", ttlSeconds, TimeUnit.SECONDS);
        log.debug("Blacklisted token {}", tokenId);
    }

    @Override
    public boolean isTokenBlacklisted(String tokenId) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(BLACKLIST_PREFIX + tokenId)
        );
    }

    // ── permission cache ──────────────────────────────────────

    @Override
    public void cachePermissions(Long userId, Long walletId, List<String> permissions) {
        String key = permissionKey(userId, walletId);
        redisTemplate.opsForValue().set(key, permissions,
                permissionCacheTtlMinutes, TimeUnit.MINUTES);
        log.debug("Cached {} permissions for user {} wallet {}", permissions.size(), userId, walletId);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getCachedPermissions(Long userId, Long walletId) {
        Object value = redisTemplate.opsForValue().get(permissionKey(userId, walletId));
        if (value instanceof List<?> list) {
            return (List<String>) list;
        }
        return null;
    }

    @Override
    public void evictPermissions(Long userId, Long walletId) {
        redisTemplate.delete(permissionKey(userId, walletId));
        log.debug("Evicted permission cache for user {} wallet {}", userId, walletId);
    }

    // ── private ───────────────────────────────────────────────

    private String permissionKey(Long userId, Long walletId) {
        return String.format(PERMISSION_PREFIX, userId, walletId);
    }
}