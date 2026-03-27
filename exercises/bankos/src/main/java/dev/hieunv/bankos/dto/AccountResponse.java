package dev.hieunv.bankos.dto;

import dev.hieunv.bankos.model.Account;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccountResponse {

    private Long id;
    private String owner;
    private BigDecimal balance;

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwner(),
                account.getBalance()
        );
    }
}
