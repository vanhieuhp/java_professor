package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class UserPermissionsResponse {

    private Long userId;
    private Long walletId;
    private String groupName;
    private List<String> permissions;   // flat list of permission codes
}