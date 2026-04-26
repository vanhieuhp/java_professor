package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.Permission;
import dev.hieunv.totp_bankos.dto.response.PermissionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PermissionMapper {

    // featureCode and functionCode come from joined data, not the entity directly
    // service resolves these and passes them in — mapped by name convention
    @Mapping(target = "featureCode",   ignore = true)
    @Mapping(target = "functionCode",  ignore = true)
    PermissionResponse toResponse(Permission permission);

    // overload used when service already resolved feature/function codes
    default PermissionResponse toResponse(
            Permission permission,
            String featureCode,
            String functionCode) {

        PermissionResponse response = toResponse(permission);
        response.setFeatureCode(featureCode);
        response.setFunctionCode(functionCode);
        return response;
    }

    List<PermissionResponse> toResponseList(List<Permission> permissions);
}