package dev.hieunv.bankos.dto.order;

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
public class OrderCreatedEvent {
    private String sagaId;
    private Long orderId;
    private Long userId;
    private Long productId;
    private int quantity;
    private BigDecimal amount;
    private LocalDateTime occurredAt;
}
