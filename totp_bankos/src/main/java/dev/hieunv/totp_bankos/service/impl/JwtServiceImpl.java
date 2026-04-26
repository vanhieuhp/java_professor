package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.User;
import dev.hieunv.totp_bankos.service.JwtService;
import dev.hieunv.totp_bankos.service.RedisService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class JwtServiceImpl implements JwtService {

    // ── claim keys ────────────────────────────────────────────
    private static final String CLAIM_USER_ID     = "userId";
    private static final String CLAIM_WALLET_ID   = "walletId";
    private static final String CLAIM_PERMISSIONS = "permissions";
    private static final String CLAIM_TOKEN_ID    = "tokenId";
    private static final String CLAIM_TYPE        = "type";
    private static final String TYPE_PRE_WALLET   = "PRE_WALLET";
    private static final String TYPE_WALLET       = "WALLET";

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.jwt.refresh-token-expiry-ms}")
    private long refreshTokenExpiryMs;

    private final RedisService redisService;

    // ── token generation ──────────────────────────────────────

    @Override
    public String generatePreWalletToken(User user, List<String> userPermissions) {
        String tokenId = UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(user.getUsername())
                .claim(CLAIM_USER_ID,     user.getId())
                .claim(CLAIM_TOKEN_ID,    tokenId)
                .claim(CLAIM_TYPE,        TYPE_PRE_WALLET)
                .claim(CLAIM_PERMISSIONS, userPermissions)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiryMs))
                .signWith(signingKey())
                .compact();
    }

    @Override
    public String generateWalletToken(Long userId, Long walletId,
                                      List<String> permissions, String tokenId) {
        long now = System.currentTimeMillis();

        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(CLAIM_USER_ID,     userId)
                .claim(CLAIM_WALLET_ID,   walletId)
                .claim(CLAIM_PERMISSIONS, permissions)
                .claim(CLAIM_TOKEN_ID,    tokenId)
                .claim(CLAIM_TYPE,        TYPE_WALLET)
                .issuedAt(new Date(now))
                .expiration(new Date(now + accessTokenExpiryMs))
                .signWith(signingKey())
                .compact();
    }

    // ── validation ────────────────────────────────────────────

    @Override
    public boolean isTokenValid(String token) {
        try {
            Claims claims = parseClaims(token);

            // 1. check expiry
            if (claims.getExpiration().before(new Date())) {
                return false;
            }

            // 2. check Redis blacklist
            String tokenId = claims.get(CLAIM_TOKEN_ID, String.class);
            if (tokenId != null && redisService.isTokenBlacklisted(tokenId)) {
                return false;
            }

            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Token validation failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Long validateRefreshToken(String refreshToken) {
        try {
            Claims claims = parseClaims(refreshToken);
            return claims.get(CLAIM_USER_ID, Long.class);
        } catch (JwtException e) {
            throw new dev.hieunv.totp_bankos.exception.UnauthorizedException("Invalid refresh token");
        }
    }

    @Override
    public String extractTokenId(String token) {
        return parseClaims(token).get(CLAIM_TOKEN_ID, String.class);
    }

    // ── package-visible helpers used by filters ───────────────

    Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    @Override
    public Long extractUserId(String token) {
        return parseClaims(token).get(CLAIM_USER_ID, Long.class);
    }

    @Override
    public Long extractWalletId(String token) {
        return parseClaims(token).get(CLAIM_WALLET_ID, Long.class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<String> extractPermissions(String token) {
        return (List<String>) parseClaims(token).get(CLAIM_PERMISSIONS);
    }

    @Override
    public String extractType(String token) {
        return parseClaims(token).get(CLAIM_TYPE, String.class);
    }

    @Override
    public boolean isWalletToken(String token) {
        return TYPE_WALLET.equals(extractType(token));
    }

    // ── private ───────────────────────────────────────────────

    private SecretKey signingKey() {
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}