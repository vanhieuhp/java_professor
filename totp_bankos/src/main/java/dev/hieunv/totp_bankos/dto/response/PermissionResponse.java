package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PermissionResponse {

    private Long id;
    private String code;           // "TRANSFER:APPROVE"
    private String featureCode;    // "TRANSFER"
    private String functionCode;   // "APPROVE"
    private String description;
}