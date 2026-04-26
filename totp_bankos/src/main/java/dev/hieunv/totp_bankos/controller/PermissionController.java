package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import dev.hieunv.totp_bankos.dto.response.UserPermissionsResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final PermissionService permissionService;

    /**
     * GET /api/permissions
     * List all 25 permissions in the system (5 features × 5 functions).
     * Use this to find the IDs when creating groups.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(permissionService.getAllPermissions()));
    }

    /**
     * GET /api/permissions/group/{groupId}
     * List permissions assigned to a specific group.
     */
    @GetMapping("/group/{groupId}")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> byGroup(
            @PathVariable Long groupId) {
        return ResponseEntity.ok(ApiResponse.ok(
                permissionService.getPermissionsByGroupId(groupId)));
    }

    /**
     * GET /api/permissions/me
     * Returns the permissions of the currently authenticated user
     * in their active wallet. Useful for frontend to show/hide UI elements.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> myPermissions() {
        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok(
                permissionService.getUserPermissionsInWallet(userId, walletId)));
    }

    /**
     * GET /api/permissions/user/{userId}/wallet/{walletId}
     * Returns what a specific user can do in a specific wallet.
     * Used by admins to inspect effective permissions.
     */
    @GetMapping("/user/{userId}/wallet/{walletId}")
    public ResponseEntity<ApiResponse<UserPermissionsResponse>> userInWallet(
            @PathVariable Long userId,
            @PathVariable Long walletId) {
        return ResponseEntity.ok(ApiResponse.ok(
                permissionService.getUserPermissionsInWallet(userId, walletId)));
    }
}