package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import dev.hieunv.totp_bankos.domain.ActiveWalletSession;
import dev.hieunv.totp_bankos.domain.User;
import dev.hieunv.totp_bankos.dto.request.ActivateWalletRequest;
import dev.hieunv.totp_bankos.dto.request.LoginRequest;
import dev.hieunv.totp_bankos.dto.response.LoginResponse;
import dev.hieunv.totp_bankos.dto.response.WalletSummaryResponse;
import dev.hieunv.totp_bankos.dto.response.WalletTokenResponse;
import dev.hieunv.totp_bankos.exception.ForbiddenException;
import dev.hieunv.totp_bankos.exception.UnauthorizedException;
import dev.hieunv.totp_bankos.mapper.AccountWalletMapper;
import dev.hieunv.totp_bankos.repository.AccountWalletRepository;
import dev.hieunv.totp_bankos.repository.ActiveWalletSessionRepository;
import dev.hieunv.totp_bankos.repository.GroupPermissionRepository;
import dev.hieunv.totp_bankos.repository.UserPermissionRepository;
import dev.hieunv.totp_bankos.repository.UserRepository;
import dev.hieunv.totp_bankos.repository.WalletUserRepository;
import dev.hieunv.totp_bankos.service.AuthService;
import dev.hieunv.totp_bankos.service.JwtService;
import dev.hieunv.totp_bankos.service.RedisService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl  implements AuthService {

    private final UserRepository userRepository;
    private final WalletUserRepository walletUserRepository;
    private final AccountWalletRepository accountWalletRepository;
    private final ActiveWalletSessionRepository sessionRepository;
    private final GroupPermissionRepository groupPermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final AccountWalletMapper walletMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final RedisService redisService;

    @Value("${app.jwt.access-token-expiry-ms}")
    private long accessTokenExpiryMs;

    @Value("${app.rbac.session-timeout-minutes}")
    private long sessionTimeoutMinutes;

    @Transactional
    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new UnauthorizedException("Invalid credentials");
        }

        if (!user.isActive()) {
            throw new UnauthorizedException("Account is disabled");
        }

        // 4. load wallets this user can access
        List<Long> walletIds = walletUserRepository.findWalletIdsByUserId(user.getId());
        List<AccountWallet> wallets = accountWalletRepository.findAllById(walletIds);
        List<WalletSummaryResponse> walletSummaries = walletMapper.toSummaryResponseList(wallets);

        // 5. load user-level permissions (e.g. ADMIN:*) and embed in pre-wallet token
        List<String> userPermissions = userPermissionRepository.findPermissionCodesByUserId(user.getId());
        String accessToken = jwtService.generatePreWalletToken(user, userPermissions);

        return LoginResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .accessToken(accessToken)
                .wallets(walletSummaries)
                .build();
    }

    @Transactional
    @Override
    public WalletTokenResponse activateWallet(Long userId, ActivateWalletRequest request) {
        Long walletId = request.getWalletId();

        // 1. confirm user has access to this wallet
        boolean isMember = walletUserRepository.existsByWalletIdAndUserIdAndIsActiveTrue(walletId, userId);
        if (!isMember) {
            throw new ForbiddenException("You do not have access to this wallet");
        }

        // 2. load wallet info
        AccountWallet wallet = accountWalletRepository.findById(walletId)
                .orElseThrow(() -> new ForbiddenException("Wallet not found"));

        // 3. resolve permissions: group permissions for this wallet + user-level permissions
        List<String> groupPermissions = groupPermissionRepository.findPermissionCodesByUserIdAndWalletId(userId, walletId);
        List<String> userPermissions  = userPermissionRepository.findPermissionCodesByUserId(userId);
        List<String> permissions = new ArrayList<>(groupPermissions);
        userPermissions.forEach(p -> { if (!permissions.contains(p)) permissions.add(p); });

        // 4. invalidate any existing session (wallet switch)
        sessionRepository.findByUserId(userId).ifPresent(existing -> {
            redisService.blacklistToken(existing.getJwtTokenId());
            sessionRepository.deleteByUserId(userId);
        });

        // 5. generate wallet-scoped JWT
        String tokenId = UUID.randomUUID().toString();
        String accessToken = jwtService.generateWalletToken(userId, walletId, permissions, tokenId);

        // 6. save active session
        ActiveWalletSession session = ActiveWalletSession.builder()
                .userId(userId)
                .walletId(walletId)
                .jwtTokenId(tokenId)
                .activatedAt(java.time.LocalDateTime.now())
                .expiresAt(java.time.LocalDateTime.now().plusMinutes(sessionTimeoutMinutes))
                .build();

        sessionRepository.save(session);

        // 7. cache permissions in Redis
        redisService.cachePermissions(userId, walletId, permissions);

        return WalletTokenResponse.builder()
                .walletId(walletId)
                .walletCode(wallet.getCode())
                .walletName(wallet.getName())
                .accessToken(accessToken)
                .permissions(permissions)
                .expiresIn(accessTokenExpiryMs / 1000)
                .build();
    }

    @Transactional
    @Override
    public void logout(Long userId) {
        sessionRepository.findByUserId(userId).ifPresent(session -> {
            redisService.blacklistToken(session.getJwtTokenId());
            redisService.evictPermissions(userId, session.getWalletId());
            sessionRepository.deleteByUserId(userId);
        });
    }

    @Override
    public String refreshToken(String refreshToken) {
        Long userId = jwtService.validateRefreshToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        List<String> userPermissions = userPermissionRepository.findPermissionCodesByUserId(userId);
        return jwtService.generatePreWalletToken(user, userPermissions);
    }
}
