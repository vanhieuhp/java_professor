package dev.hieunv.bankos.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayRequest {

    private Long accountId;
    private BigDecimal amount;
    private String idempotencyKey;
    private boolean simulateFailure;   // k6 sets this to true to force failures
}
