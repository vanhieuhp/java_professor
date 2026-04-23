package dev.hieunv.bankos.dto.payment;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PaymentStatEvent {
    private String accountId;
    private Long paymentCount;
    private Instant windowStart;
    private Instant windowEnd;
}