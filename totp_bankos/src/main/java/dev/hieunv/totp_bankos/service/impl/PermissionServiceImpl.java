package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.Feature;
import dev.hieunv.totp_bankos.domain.Function;
import dev.hieunv.totp_bankos.domain.Group;
import dev.hieunv.totp_bankos.domain.GroupPermission;
import dev.hieunv.totp_bankos.domain.Permission;
import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import dev.hieunv.totp_bankos.dto.response.UserPermissionsResponse;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.PermissionMapper;
import dev.hieunv.totp_bankos.repository.FeatureRepository;
import dev.hieunv.totp_bankos.repository.FunctionRepository;
import dev.hieunv.totp_bankos.repository.GroupPermissionRepository;
import dev.hieunv.totp_bankos.repository.GroupRepository;
import dev.hieunv.totp_bankos.repository.PermissionRepository;
import dev.hieunv.totp_bankos.repository.WalletUserGroupRepository;
import dev.hieunv.totp_bankos.service.PermissionService;
import dev.hieunv.totp_bankos.service.RedisService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository      permissionRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final WalletUserGroupRepository walletUserGroupRepository;
    private final GroupRepository           groupRepository;
    private final FeatureRepository         featureRepository;
    private final FunctionRepository        functionRepository;
    private final PermissionMapper          permissionMapper;
    private final RedisService              redisService;

    @Override
    public List<PermissionResponse> getAllPermissions() {
        return permissionRepository.findAll()
                .stream()
                .map(this::buildPermissionResponse)
                .toList();
    }

    @Override
    public List<PermissionResponse> getPermissionsByGroupId(Long groupId) {
        List<GroupPermission> groupPermissions =
                groupPermissionRepository.findByGroupId(groupId);

        List<Long> permissionIds = groupPermissions.stream()
                .map(GroupPermission::getPermissionId)
                .toList();

        return permissionRepository.findAllById(permissionIds)
                .stream()
                .map(this::buildPermissionResponse)
                .toList();
    }

    @Override
    public UserPermissionsResponse getUserPermissionsInWallet(Long userId, Long walletId) {
        // try Redis cache first
        List<String> cached = redisService.getCachedPermissions(userId, walletId);
        if (cached != null) {
            return UserPermissionsResponse.builder()
                    .userId(userId)
                    .walletId(walletId)
                    .permissions(cached)
                    .build();
        }

        // resolve from DB
        List<String> permissionCodes = groupPermissionRepository
                .findPermissionCodesByUserIdAndWalletId(userId, walletId);

        // resolve group name for context
        String groupName = walletUserGroupRepository
                .findByWalletIdAndUserIdAndIsActiveTrue(walletId, userId)
                .flatMap(wug -> groupRepository.findById(wug.getGroupId()))
                .map(Group::getName)
                .orElse("No group assigned");

        return UserPermissionsResponse.builder()
                .userId(userId)
                .walletId(walletId)
                .groupName(groupName)
                .permissions(permissionCodes)
                .build();
    }

    @Override
    public boolean userHasPermission(Long userId, Long walletId, String permissionCode) {
        // check Redis first
        List<String> cached = redisService.getCachedPermissions(userId, walletId);
        if (cached != null) {
            return cached.contains(permissionCode);
        }

        // fall back to DB
        List<String> permissions = groupPermissionRepository
                .findPermissionCodesByUserIdAndWalletId(userId, walletId);
        return permissions.contains(permissionCode);
    }

    // ── private helper ──────────────────────────────────────────

    private PermissionResponse buildPermissionResponse(Permission permission) {
        // parse codes from permission.code e.g. "TRANSFER:APPROVE"
        String[] parts = permission.getCode().split(":");
        String featureCode  = parts.length > 0 ? parts[0] : "";
        String functionCode = parts.length > 1 ? parts[1] : "";
        return permissionMapper.toResponse(permission, featureCode, functionCode);
    }
}