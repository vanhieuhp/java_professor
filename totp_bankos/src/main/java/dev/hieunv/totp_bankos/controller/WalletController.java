package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.AssignUserToWalletRequest;
import dev.hieunv.totp_bankos.dto.request.CreateWalletRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import dev.hieunv.totp_bankos.dto.response.WalletSummaryResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/wallets")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    /**
     * POST /api/wallets
     * Create a new wallet under a CIF.
     */
    @PostMapping
    @RequiresPermission("ADMIN:CREATE_WALLET")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> createWallet(
            @Valid @RequestBody CreateWalletRequest request) {

        WalletSummaryResponse wallet = walletService.createWallet(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Wallet created", wallet));
    }

    /**
     * GET /api/wallets/{walletId}
     * Get wallet details by ID.
     */
    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletSummaryResponse>> getWallet(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(ApiResponse.ok(walletService.getWallet(walletId)));
    }

    /**
     * GET /api/wallets/cif/{cifId}
     * List all active wallets belonging to a CIF.
     */
    @GetMapping("/cif/{cifId}")
    public ResponseEntity<ApiResponse<List<WalletSummaryResponse>>> getWalletsByCif(
            @PathVariable Long cifId) {

        return ResponseEntity.ok(ApiResponse.ok(walletService.getWalletsByCif(cifId)));
    }

    /**
     * GET /api/wallets/{walletId}/users
     * List all users assigned to a wallet.
     */
    @GetMapping("/{walletId}/users")
    public ResponseEntity<ApiResponse<List<UserResponse>>> getUsersInWallet(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(ApiResponse.ok(walletService.getUsersInWallet(walletId)));
    }

    /**
     * POST /api/wallets/assign-user
     * Assign a user to a wallet.
     */
    @PostMapping("/assign-user")
    @RequiresPermission("ADMIN:ASSIGN_USER")
    public ResponseEntity<ApiResponse<Void>> assignUser(
            @Valid @RequestBody AssignUserToWalletRequest request) {

        Long assignedBy = AppSecurityContext.getUserId();
        walletService.assignUserToWallet(request, assignedBy);
        return ResponseEntity.ok(ApiResponse.ok("User assigned to wallet", null));
    }

    /**
     * DELETE /api/wallets/{walletId}/users/{userId}
     * Remove a user from a wallet (soft-delete).
     */
    @DeleteMapping("/{walletId}/users/{userId}")
    public ResponseEntity<ApiResponse<Void>> removeUser(
            @PathVariable Long walletId,
            @PathVariable Long userId) {

        walletService.removeUserFromWallet(walletId, userId);
        return ResponseEntity.ok(ApiResponse.ok("User removed from wallet", null));
    }
}