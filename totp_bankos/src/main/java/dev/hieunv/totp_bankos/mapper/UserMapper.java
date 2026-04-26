package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.User;
import dev.hieunv.totp_bankos.dto.request.CreateUserRequest;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

    // password is set separately (needs bcrypt) — ignored here
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "password",  ignore = true)
    @Mapping(target = "isActive",  constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    User toEntity(CreateUserRequest request);

    UserResponse toResponse(User user);

    List<UserResponse> toResponseList(List<User> users);
}