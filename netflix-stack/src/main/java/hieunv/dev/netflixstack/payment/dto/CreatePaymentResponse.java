package hieunv.dev.netflixstack.payment.dto;

import hieunv.dev.netflixstack.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CreatePaymentResponse(Long paymentId,
                                    Long userId,
                                    BigDecimal amount,
                                    String currency,
                                    PaymentStatus status,
                                    String stripeChargeId,
                                    String failureReason,
                                    Instant createdAt,
                                    boolean replayed) {

    public CreatePaymentResponse asReplay() {
        return new CreatePaymentResponse(paymentId, userId, amount, currency, status,
                stripeChargeId, failureReason, createdAt, true);
    }
}
