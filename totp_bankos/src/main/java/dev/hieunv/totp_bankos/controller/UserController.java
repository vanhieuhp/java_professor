package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.CreateUserRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping
    @RequiresPermission("ADMIN:CREATE_USER")
    public ResponseEntity<ApiResponse<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("User created", userService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(userService.getById(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(userService.listAll()));
    }

    @PatchMapping("/{id}/activate")
    public ResponseEntity<ApiResponse<UserResponse>> activate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User activated", userService.activate(id)));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<UserResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("User deactivated", userService.deactivate(id)));
    }
}