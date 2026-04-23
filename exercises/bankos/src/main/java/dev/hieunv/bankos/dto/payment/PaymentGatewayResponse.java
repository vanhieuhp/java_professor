package dev.hieunv.bankos.dto.payment;

import dev.hieunv.bankos.enums.GatewayStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {
    private GatewayStatus status;
    private String gatewayRef;
    private String message;
}
