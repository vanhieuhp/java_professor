package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.domain.User;

import java.util.List;

public interface JwtService {

    // issued right after login — carries user-level permissions (e.g. ADMIN:*)
    String generatePreWalletToken(User user, List<String> userPermissions);

    // issued after wallet activation — carries permissions[]
    String generateWalletToken(Long userId, Long walletId,
                               List<String> permissions, String tokenId);

    // validate and return userId from refresh token
    Long validateRefreshToken(String refreshToken);

    // extract tokenId claim from any token
    String extractTokenId(String token);

    // check token has not expired and is not blacklisted
    boolean isTokenValid(String token);

    Long extractUserId(String token);

    Long extractWalletId(String token);

    @SuppressWarnings("unchecked")
    List<String> extractPermissions(String token);

    String extractType(String token);

    boolean isWalletToken(String token);
}