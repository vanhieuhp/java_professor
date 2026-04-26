package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.CreateUserRequest;
import dev.hieunv.totp_bankos.dto.response.UserResponse;

import java.util.List;

public interface UserService {

    UserResponse create(CreateUserRequest request);

    UserResponse getById(Long id);

    List<UserResponse> listAll();

    UserResponse activate(Long id);

    UserResponse deactivate(Long id);
}