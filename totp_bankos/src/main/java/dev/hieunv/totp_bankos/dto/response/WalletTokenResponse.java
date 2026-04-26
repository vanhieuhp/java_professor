package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WalletTokenResponse {

    private Long walletId;
    private String walletCode;
    private String walletName;
    private String accessToken;          // wallet-scoped JWT
    private List<String> permissions;    // e.g. ["TRANSFER:CREATE_REQUEST", "CASHIN:LIST"]
    private Long expiresIn;              // seconds until expiry
}