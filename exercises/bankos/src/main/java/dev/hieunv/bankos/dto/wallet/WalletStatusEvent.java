package dev.hieunv.bankos.dto.wallet;

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
public class WalletStatusEvent {
    private Long accountId;
    private BigDecimal balance;
    private String status;
    private LocalDateTime occurredAt;
}
