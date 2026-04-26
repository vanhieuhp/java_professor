package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.AssignUserToGroupRequest;
import dev.hieunv.totp_bankos.dto.request.CreateGroupRequest;
import dev.hieunv.totp_bankos.dto.request.UpdateGroupPermissionsRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.GroupResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    /**
     * POST /api/groups
     * Create a new group inside a wallet, optionally pre-assigning permissions.
     */
    @PostMapping
    @RequiresPermission("ADMIN:MANAGE_GROUP")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {

        Long createdBy = AppSecurityContext.getUserId();
        GroupResponse group = groupService.createGroup(request, createdBy);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Group created", group));
    }

    /**
     * GET /api/groups/{groupId}
     * Get a single group with its permissions.
     */
    @GetMapping("/{groupId}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(
            @PathVariable Long groupId) {

        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroup(groupId)));
    }

    /**
     * GET /api/groups/wallet/{walletId}
     * List all active groups in a wallet.
     */
    @GetMapping("/wallet/{walletId}")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> getGroupsByWallet(
            @PathVariable Long walletId) {

        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupsByWallet(walletId)));
    }

    /**
     * POST /api/groups/assign-user
     * Assign a user to a group within a wallet.
     * If the user is already in another group in the same wallet, they are moved.
     */
    @PostMapping("/assign-user")
    @RequiresPermission("ADMIN:MANAGE_GROUP")
    public ResponseEntity<ApiResponse<Void>> assignUser(
            @Valid @RequestBody AssignUserToGroupRequest request) {

        Long assignedBy = AppSecurityContext.getUserId();
        groupService.assignUserToGroup(request, assignedBy);
        return ResponseEntity.ok(ApiResponse.ok("User assigned to group", null));
    }

    /**
     * PUT /api/groups/permissions
     * Replace all permissions on a group (full replacement, not patch).
     */
    @PutMapping("/permissions")
    @RequiresPermission("ADMIN:MANAGE_GROUP")
    public ResponseEntity<ApiResponse<Void>> updatePermissions(
            @Valid @RequestBody UpdateGroupPermissionsRequest request) {

        Long updatedBy = AppSecurityContext.getUserId();
        groupService.updateGroupPermissions(request, updatedBy);
        return ResponseEntity.ok(ApiResponse.ok("Group permissions updated", null));
    }

    /**
     * DELETE /api/groups/{groupId}
     * Soft-delete a group (sets is_active = false).
     */
    @DeleteMapping("/{groupId}")
    @RequiresPermission("ADMIN:MANAGE_GROUP")
    public ResponseEntity<ApiResponse<Void>> deleteGroup(
            @PathVariable Long groupId) {

        groupService.deleteGroup(groupId);
        return ResponseEntity.ok(ApiResponse.ok("Group deleted", null));
    }
}