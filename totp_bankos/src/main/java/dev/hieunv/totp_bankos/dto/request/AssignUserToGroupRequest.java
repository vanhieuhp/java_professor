package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignUserToGroupRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Group ID is required")
    private Long groupId;

    @NotNull(message = "Wallet ID is required")
    private Long walletId;
}