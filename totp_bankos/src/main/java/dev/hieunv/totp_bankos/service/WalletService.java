package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.AssignUserToWalletRequest;
import dev.hieunv.totp_bankos.dto.request.CreateWalletRequest;
import dev.hieunv.totp_bankos.dto.response.UserResponse;
import dev.hieunv.totp_bankos.dto.response.WalletSummaryResponse;

import java.util.List;

public interface WalletService {
    WalletSummaryResponse createWallet(CreateWalletRequest request);

    WalletSummaryResponse getWallet(Long walletId);

    List<WalletSummaryResponse> getWalletsByCif(Long cifId);

    List<UserResponse> getUsersInWallet(Long walletId);

    void assignUserToWallet(AssignUserToWalletRequest request, Long assignedBy);

    void removeUserFromWallet(Long walletId, Long userId);
}
