package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.AssignUserToGroupRequest;
import dev.hieunv.totp_bankos.dto.request.CreateGroupRequest;
import dev.hieunv.totp_bankos.dto.request.UpdateGroupPermissionsRequest;
import dev.hieunv.totp_bankos.dto.response.GroupResponse;

import java.util.List;

public interface GroupService {

    GroupResponse createGroup(CreateGroupRequest request, Long createdBy);

    GroupResponse getGroup(Long groupId);

    List<GroupResponse> getGroupsByWallet(Long walletId);

    void assignUserToGroup(AssignUserToGroupRequest request, Long assignedBy);

    void updateGroupPermissions(UpdateGroupPermissionsRequest request, Long updatedBy);

    void deleteGroup(Long groupId);
}