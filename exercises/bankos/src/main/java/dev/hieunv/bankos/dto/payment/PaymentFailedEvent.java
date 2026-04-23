package dev.hieunv.bankos.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent {
    private String sagaId;
    private Long orderId;
    private Long productId;
    private String reason;
    private LocalDateTime occurredAt;
}
