package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateWalletRequest {

    @NotNull(message = "CIF ID is required")
    private Long cifId;

    @NotBlank(message = "Wallet code is required")
    private String code;

    @NotBlank(message = "Wallet name is required")
    private String name;

    private String currency = "VND";
}