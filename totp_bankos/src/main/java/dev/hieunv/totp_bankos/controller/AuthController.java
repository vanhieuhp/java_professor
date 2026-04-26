package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.ActivateWalletRequest;
import dev.hieunv.totp_bankos.dto.request.LoginRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.LoginResponse;
import dev.hieunv.totp_bankos.dto.response.WalletTokenResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/auth/login
     * Public — no token required.
     * Returns a pre-wallet access token + list of wallets the user can activate.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok("Login successful", response));
    }

    /**
     * POST /api/auth/activate-wallet
     * Requires a valid pre-wallet token.
     * Returns a wallet-scoped token with resolved permissions embedded.
     */
    @PostMapping("/activate-wallet")
    public ResponseEntity<ApiResponse<WalletTokenResponse>> activateWallet(
            @Valid @RequestBody ActivateWalletRequest request) {

        Long userId = AppSecurityContext.getUserId();
        WalletTokenResponse response = authService.activateWallet(userId, request);
        return ResponseEntity.ok(ApiResponse.ok("Wallet activated", response));
    }

    /**
     * POST /api/auth/logout
     * Blacklists the current token and clears the active wallet session.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout() {
        Long userId = AppSecurityContext.getUserId();
        authService.logout(userId);
        return ResponseEntity.ok(ApiResponse.ok("Logged out successfully", null));
    }

    /**
     * POST /api/auth/refresh
     * Public — accepts refresh token, returns a new pre-wallet access token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<String>> refresh(
            @RequestHeader("X-Refresh-Token") String refreshToken) {

        String newToken = authService.refreshToken(refreshToken);
        return ResponseEntity.ok(ApiResponse.ok("Token refreshed", newToken));
    }
}