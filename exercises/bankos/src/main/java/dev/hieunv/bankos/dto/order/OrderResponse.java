package dev.hieunv.bankos.dto.order;

import dev.hieunv.bankos.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long orderId;
    private String sagaId;
    private OrderStatus status;
    private String message;
}
