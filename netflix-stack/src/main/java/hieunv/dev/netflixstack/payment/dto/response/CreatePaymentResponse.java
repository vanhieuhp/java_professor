package hieunv.dev.netflixstack.payment.dto.response;

import hieunv.dev.netflixstack.payment.entity.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentResponse {

    private Long paymentId;
    private Long userId;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String stripeChargeId;
    private String failureReason;
    private Instant createdAt;
    private boolean replayed;

    public CreatePaymentResponse asReplay() {
        return new CreatePaymentResponse(paymentId, userId, amount, currency, status,
                stripeChargeId, failureReason, createdAt, true);
    }
}
