package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GroupResponse {

    private Long id;
    private Long walletId;
    private String name;
    private String description;
    private boolean isActive;
    private List<PermissionResponse> permissions;
}