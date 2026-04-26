package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.domain.User;

import java.util.List;

public interface JwtService {

    // issued right after login — no wallet scope yet
    String generatePreWalletToken(User user);

    // issued after wallet activation — carries permissions[]
    String generateWalletToken(Long userId, Long walletId,
                               List<String> permissions, String tokenId);

    // validate and return userId from refresh token
    Long validateRefreshToken(String refreshToken);

    // extract tokenId claim from any token
    String extractTokenId(String token);

    // check token has not expired and is not blacklisted
    boolean isTokenValid(String token);
}