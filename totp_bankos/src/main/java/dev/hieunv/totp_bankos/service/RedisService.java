package dev.hieunv.totp_bankos.service;

import java.util.List;

public interface RedisService {

    // blacklist a token so it's rejected even before expiry
    void blacklistToken(String tokenId);

    boolean isTokenBlacklisted(String tokenId);

    // cache resolved permissions after wallet activation
    void cachePermissions(Long userId, Long walletId, List<String> permissions);

    List<String> getCachedPermissions(Long userId, Long walletId);

    void evictPermissions(Long userId, Long walletId);
}