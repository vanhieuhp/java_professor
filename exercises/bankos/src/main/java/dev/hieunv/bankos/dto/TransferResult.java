package dev.hieunv.bankos.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferResult {

    private Long fromAccountId;
    private Long toAccountId;
    private BigDecimal amount;
    private BigDecimal fromBalanceAfter;
    private BigDecimal toBalanceAfter;

}
