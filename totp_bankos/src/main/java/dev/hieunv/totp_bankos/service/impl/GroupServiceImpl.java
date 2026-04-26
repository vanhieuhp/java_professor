package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.Group;
import dev.hieunv.totp_bankos.domain.GroupPermission;
import dev.hieunv.totp_bankos.domain.Permission;
import dev.hieunv.totp_bankos.domain.WalletUserGroup;
import dev.hieunv.totp_bankos.dto.request.AssignUserToGroupRequest;
import dev.hieunv.totp_bankos.dto.request.CreateGroupRequest;
import dev.hieunv.totp_bankos.dto.request.UpdateGroupPermissionsRequest;
import dev.hieunv.totp_bankos.dto.response.GroupResponse;
import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.GroupMapper;
import dev.hieunv.totp_bankos.mapper.PermissionMapper;
import dev.hieunv.totp_bankos.repository.GroupPermissionRepository;
import dev.hieunv.totp_bankos.repository.GroupRepository;
import dev.hieunv.totp_bankos.repository.PermissionRepository;
import dev.hieunv.totp_bankos.repository.WalletUserGroupRepository;
import dev.hieunv.totp_bankos.repository.WalletUserRepository;
import dev.hieunv.totp_bankos.service.GroupService;
import dev.hieunv.totp_bankos.service.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final GroupRepository           groupRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final WalletUserGroupRepository walletUserGroupRepository;
    private final WalletUserRepository      walletUserRepository;
    private final PermissionRepository      permissionRepository;
    private final GroupMapper               groupMapper;
    private final PermissionMapper          permissionMapper;
    private final PermissionService         permissionService;

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request, Long createdBy) {
        // prevent duplicate group name in same wallet
        if (groupRepository.existsByWalletIdAndName(request.getWalletId(), request.getName())) {
            throw new BadRequestException(
                    "Group '" + request.getName() + "' already exists in this wallet"
            );
        }

        // save group
        Group group = groupMapper.toEntity(request);
        group = groupRepository.save(group);

        // assign initial permissions if provided
        if (!request.getPermissionIds().isEmpty()) {
            Long groupId = group.getId();
            List<GroupPermission> groupPermissions = request.getPermissionIds().stream()
                    .map(permId -> GroupPermission.builder()
                            .groupId(groupId)
                            .permissionId(permId)
                            .grantedBy(createdBy)
                            .grantedAt(LocalDateTime.now())
                            .build())
                    .toList();
            groupPermissionRepository.saveAll(groupPermissions);
        }

        return buildGroupResponse(group);
    }

    @Override
    public GroupResponse getGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        return buildGroupResponse(group);
    }

    @Override
    public List<GroupResponse> getGroupsByWallet(Long walletId) {
        return groupRepository.findByWalletIdAndIsActiveTrue(walletId)
                .stream()
                .map(this::buildGroupResponse)
                .toList();
    }

    @Override
    @Transactional
    public void assignUserToGroup(AssignUserToGroupRequest request, Long assignedBy) {
        // verify user is a member of the wallet first
        if (!walletUserRepository.existsByWalletIdAndUserIdAndIsActiveTrue(
                request.getWalletId(), request.getUserId())) {
            throw new BadRequestException("User is not a member of this wallet");
        }

        // verify group belongs to the wallet
        Group group = groupRepository.findById(request.getGroupId())
                .orElseThrow(() -> new NotFoundException("Group not found"));
        if (!group.getWalletId().equals(request.getWalletId())) {
            throw new BadRequestException("Group does not belong to this wallet");
        }

        // update or create assignment
        walletUserGroupRepository
                .findByWalletIdAndUserIdAndIsActiveTrue(request.getWalletId(), request.getUserId())
                .ifPresentOrElse(
                        existing -> {
                            // user already in a group → reassign
                            existing.setGroupId(request.getGroupId());
                            existing.setAssignedBy(assignedBy);
                            existing.setAssignedAt(LocalDateTime.now());
                            walletUserGroupRepository.save(existing);
                        },
                        () -> {
                            // first assignment
                            WalletUserGroup assignment = WalletUserGroup.builder()
                                    .walletId(request.getWalletId())
                                    .userId(request.getUserId())
                                    .groupId(request.getGroupId())
                                    .assignedBy(assignedBy)
                                    .assignedAt(LocalDateTime.now())
                                    .isActive(true)
                                    .build();
                            walletUserGroupRepository.save(assignment);
                        }
                );
    }

    @Override
    @Transactional
    public void updateGroupPermissions(UpdateGroupPermissionsRequest request, Long updatedBy) {
        // verify group exists
        if (!groupRepository.existsById(request.getGroupId())) {
            throw new NotFoundException("Group not found");
        }

        // full replacement — delete all then re-insert
        groupPermissionRepository.deleteAllByGroupId(request.getGroupId());

        List<GroupPermission> newPermissions = request.getPermissionIds().stream()
                .map(permId -> GroupPermission.builder()
                        .groupId(request.getGroupId())
                        .permissionId(permId)
                        .grantedBy(updatedBy)
                        .grantedAt(LocalDateTime.now())
                        .build())
                .toList();

        groupPermissionRepository.saveAll(newPermissions);
    }

    @Override
    @Transactional
    public void deleteGroup(Long groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new NotFoundException("Group not found"));
        group.setActive(false);
        groupRepository.save(group);
    }

    // ── private helper ─────────────────────────────────────────

    private GroupResponse buildGroupResponse(Group group) {
        List<PermissionResponse> permissions = permissionService
                .getPermissionsByGroupId(group.getId());
        return groupMapper.toResponse(group, permissions);
    }
}