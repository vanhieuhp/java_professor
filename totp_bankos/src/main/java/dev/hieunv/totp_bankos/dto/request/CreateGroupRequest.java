package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CreateGroupRequest {

    @NotNull(message = "Wallet ID is required")
    private Long walletId;

    @NotBlank(message = "Group name is required")
    private String name;

    private String description;

    // permission IDs to assign to this group on creation
    private List<Long> permissionIds = new ArrayList<>();
}