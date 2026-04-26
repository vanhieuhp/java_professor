package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class LoginResponse {

    private Long userId;
    private String username;
    private String fullName;
    private String accessToken;       // no wallet scope yet
    private String refreshToken;
    private List<WalletSummaryResponse> wallets;  // wallets this user can access
}