package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ActivateWalletRequest {

    @NotNull(message = "Wallet ID is required")
    private Long walletId;
}