package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.ActivateWalletRequest;
import dev.hieunv.totp_bankos.dto.request.LoginRequest;
import dev.hieunv.totp_bankos.dto.response.LoginResponse;
import dev.hieunv.totp_bankos.dto.response.WalletTokenResponse;

public interface AuthService {
    // authenticate user, return access token + list of accessible wallets
    LoginResponse login(LoginRequest request);

    // select a wallet → resolve permissions → return wallet-scoped token
    WalletTokenResponse activateWallet(Long userId, ActivateWalletRequest request);

    // invalidate current session
    void logout(Long userId);

    // issue new access token from refresh token
    String refreshToken(String refreshToken);
}
