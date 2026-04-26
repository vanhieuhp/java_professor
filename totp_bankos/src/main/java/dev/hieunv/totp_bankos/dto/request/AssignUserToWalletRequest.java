package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignUserToWalletRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Wallet ID is required")
    private Long walletId;
}