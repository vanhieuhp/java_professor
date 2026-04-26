package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import dev.hieunv.totp_bankos.dto.response.UserPermissionsResponse;

import java.util.List;

public interface PermissionService {

    // all permissions available in the system
    List<PermissionResponse> getAllPermissions();

    // permissions assigned to a specific group
    List<PermissionResponse> getPermissionsByGroupId(Long groupId);

    // resolve what a specific user can do in a specific wallet
    UserPermissionsResponse getUserPermissionsInWallet(Long userId, Long walletId);

    // check a single permission — used by service layer for maker/checker
    boolean userHasPermission(Long userId, Long walletId, String permissionCode);
}