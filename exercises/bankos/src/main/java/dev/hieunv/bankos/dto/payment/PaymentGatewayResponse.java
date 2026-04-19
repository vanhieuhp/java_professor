package dev.hieunv.bankos.dto.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentGatewayResponse {
    private String status;       // "SUCCESS", "FAILED"
    private String gatewayRef;   // reference ID từ gateway
    private String message;
}
