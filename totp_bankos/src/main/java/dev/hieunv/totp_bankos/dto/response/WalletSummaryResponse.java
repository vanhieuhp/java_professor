package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class WalletSummaryResponse {

    private Long id;
    private String code;
    private String name;
    private BigDecimal balance;
    private String currency;
}