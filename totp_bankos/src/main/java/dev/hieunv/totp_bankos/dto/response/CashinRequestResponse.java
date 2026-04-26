package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class CashinRequestResponse {
    private Long          id;
    private Long          walletId;
    private Long          createdBy;
    private LocalDateTime createdAt;
    private Long          reviewedBy;
    private LocalDateTime reviewedAt;
    private String        rejectionNote;
    private BigDecimal    amount;
    private String        currency;
    private String        fromAccount;
    private String        description;
    private String        status;
}