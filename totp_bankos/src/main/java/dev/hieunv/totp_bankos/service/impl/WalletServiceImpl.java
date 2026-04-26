package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import dev.hieunv.totp_bankos.domain.WalletUser;
import dev.hieunv.totp_bankos.dto.request.AssignUserToWalletRequest;
import dev.hieunv.totp_bankos.dto.request.CreateWalletRequest;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import dev.hieunv.totp_bankos.dto.response.WalletSummaryResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.AccountWalletMapper;
import dev.hieunv.totp_bankos.mapper.UserMapper;
import dev.hieunv.totp_bankos.repository.AccountWalletRepository;
import dev.hieunv.totp_bankos.repository.CifRepository;
import dev.hieunv.totp_bankos.repository.UserRepository;
import dev.hieunv.totp_bankos.repository.WalletUserRepository;
import dev.hieunv.totp_bankos.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final AccountWalletRepository walletRepository;
    private final WalletUserRepository    walletUserRepository;
    private final UserRepository          userRepository;
    private final CifRepository           cifRepository;
    private final AccountWalletMapper     walletMapper;
    private final UserMapper              userMapper;

    @Override
    @Transactional
    public WalletSummaryResponse createWallet(CreateWalletRequest request) {
        // verify CIF exists
        if (!cifRepository.existsById(request.getCifId())) {
            throw new NotFoundException("CIF not found");
        }

        // prevent duplicate wallet code
        if (walletRepository.existsByCode(request.getCode())) {
            throw new BadRequestException("Wallet code already exists: " + request.getCode());
        }

        AccountWallet wallet = walletMapper.toEntity(request);
        return walletMapper.toSummaryResponse(walletRepository.save(wallet));
    }

    @Override
    public WalletSummaryResponse getWallet(Long walletId) {
        AccountWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));
        return walletMapper.toSummaryResponse(wallet);
    }

    @Override
    public List<WalletSummaryResponse> getWalletsByCif(Long cifId) {
        List<AccountWallet> wallets = walletRepository.findByCifIdAndIsActiveTrue(cifId);
        return walletMapper.toSummaryResponseList(wallets);
    }

    @Override
    public List<UserResponse> getUsersInWallet(Long walletId) {
        List<WalletUser> members = walletUserRepository.findByWalletIdAndIsActiveTrue(walletId);
        List<Long> userIds = members.stream().map(WalletUser::getUserId).toList();
        return userMapper.toResponseList(userRepository.findAllById(userIds));
    }

    @Override
    @Transactional
    public void assignUserToWallet(AssignUserToWalletRequest request, Long assignedBy) {
        // verify wallet and user exist
        if (!walletRepository.existsById(request.getWalletId())) {
            throw new NotFoundException("Wallet not found");
        }
        if (!userRepository.existsById(request.getUserId())) {
            throw new NotFoundException("User not found");
        }

        // prevent duplicate membership
        if (walletUserRepository.existsByWalletIdAndUserIdAndIsActiveTrue(
                request.getWalletId(), request.getUserId())) {
            throw new BadRequestException("User is already a member of this wallet");
        }

        WalletUser member = WalletUser.builder()
                .walletId(request.getWalletId())
                .userId(request.getUserId())
                .assignedBy(assignedBy)
                .assignedAt(LocalDateTime.now())
                .isActive(true)
                .build();

        walletUserRepository.save(member);
    }

    @Override
    @Transactional
    public void removeUserFromWallet(Long walletId, Long userId) {
        WalletUser member = walletUserRepository
                .findByWalletIdAndUserId(walletId, userId)
                .orElseThrow(() -> new NotFoundException("User is not a member of this wallet"));

        member.setActive(false);
        walletUserRepository.save(member);
    }
}