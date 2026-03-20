package dev.hieunv.grpcclient.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class WalletAccountDto {

    private Long accountId;
    private String accountNumber;
    private String customerId;
    private String accountStatus;
    private String customerStatus;
    private String currency;
    private String availableBalance;
    private String currentBalance;
    private String earmarkBalance;
    private String overdraftLimit;
    private String paylaterBalance;
    private String paylaterLimit;
    private String monthlyLimitAmount;
    private String remainingDailyAmount;
    private String remainingMonthlyAmount;
}
