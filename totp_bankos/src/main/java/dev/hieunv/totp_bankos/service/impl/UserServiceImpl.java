package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.User;
import dev.hieunv.totp_bankos.dto.request.CreateUserRequest;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.UserMapper;
import dev.hieunv.totp_bankos.repository.UserRepository;
import dev.hieunv.totp_bankos.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository  userRepository;
    private final UserMapper      userMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new BadRequestException("Username already taken: " + request.getUsername());
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("Email already registered: " + request.getEmail());
        }

        User user = userMapper.toEntity(request);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    public UserResponse getById(Long id) {
        return userMapper.toResponse(findById(id));
    }

    @Override
    public List<UserResponse> listAll() {
        return userMapper.toResponseList(userRepository.findAll());
    }

    @Override
    @Transactional
    public UserResponse activate(Long id) {
        User user = findById(id);
        user.setActive(true);
        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserResponse deactivate(Long id) {
        User user = findById(id);
        user.setActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        return userMapper.toResponse(userRepository.save(user));
    }

    // ── private ───────────────────────────────────────────────

    private User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }
}