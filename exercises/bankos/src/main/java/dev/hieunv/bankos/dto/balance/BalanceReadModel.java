package dev.hieunv.bankos.dto.balance;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BalanceReadModel {
    private Long accountId;
    private BigDecimal balance;
    private Long lastPaymentId;
    private LocalDateTime lastUpdatedAt;
}
