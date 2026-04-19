package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.payment.PaymentGatewayRequest;
import dev.hieunv.bankos.dto.payment.PaymentGatewayResponse;
import dev.hieunv.bankos.model.Payment;
import jakarta.transaction.Transactional;

import java.math.BigDecimal;

public interface PaymentService {

    Payment processPayment(Long accountId, BigDecimal amount);


    @Transactional
    Payment processPaymentIdempotent(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey);

    @Transactional
    Payment insertPendingPayment(Long accountId, BigDecimal amount);

    /* Resilience4j */
    PaymentGatewayResponse callGatewayWithResilience(PaymentGatewayRequest request);

    PaymentGatewayResponse gatewayFallback(PaymentGatewayRequest request, Throwable t);

    Payment processPaymentWithOutbox(Long accountId, BigDecimal amount, String idempotencyKey);

    Payment processPaymentSafe(Long accountId, BigDecimal amount, String idempotencyKey);
}
