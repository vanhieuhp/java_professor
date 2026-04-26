package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.Group;
import dev.hieunv.totp_bankos.dto.request.CreateGroupRequest;
import dev.hieunv.totp_bankos.dto.response.GroupResponse;
import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface GroupMapper {

    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "isActive",  constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    Group toEntity(CreateGroupRequest request);

    // permissions list is populated by the service, not MapStruct
    @Mapping(target = "permissions", ignore = true)
    GroupResponse toResponse(Group group);

    // overload used when service already resolved permissions
    default GroupResponse toResponse(Group group, List<PermissionResponse> permissions) {
        GroupResponse response = toResponse(group);
        response.setPermissions(permissions);
        return response;
    }

    List<GroupResponse> toResponseList(List<Group> groups);
}