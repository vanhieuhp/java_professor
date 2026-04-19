package dev.hieunv.bankos.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderRequest {
    private Long userId;
    private Long productId;
    private int quantity;
    private BigDecimal amount;     // payment amount
    private boolean simulatePaymentFailure;  // for testing compensation
}
