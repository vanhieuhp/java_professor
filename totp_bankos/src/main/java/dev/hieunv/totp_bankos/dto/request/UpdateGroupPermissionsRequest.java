package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class UpdateGroupPermissionsRequest {

    @NotNull(message = "Group ID is required")
    private Long groupId;

    @NotNull(message = "Wallet ID is required")
    private Long walletId;

    // full replacement — replaces existing permissions entirely
    @NotNull(message = "Permission IDs are required")
    private List<Long> permissionIds;
}