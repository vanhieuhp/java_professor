package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.payment.PaymentGatewayRequest;
import dev.hieunv.bankos.dto.payment.PaymentGatewayResponse;
import dev.hieunv.bankos.dto.payment.PaymentRequest;
import dev.hieunv.bankos.enums.GatewayStatus;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing with Resilience4j")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/gateway")
    @Operation(summary = "Call gateway with Bulkhead + CircuitBreaker + Retry")
    public ResponseEntity<PaymentGatewayResponse> callGateway(
            @RequestBody PaymentGatewayRequest request) {
        PaymentGatewayResponse response = paymentService.callGatewayWithResilience(request);
        return response.getStatus() == GatewayStatus.SUCCESS
                ? ResponseEntity.ok(response)
                : ResponseEntity.status(503).body(response);
    }

    @PostMapping("/with-outbox")
    @Operation(summary = "Process payment + write outbox event atomically")
    public ResponseEntity<Payment> processWithOutbox(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {
        Payment payment = paymentService.processPaymentWithOutbox(
                request.getAccountId(), request.getAmount(), idempotencyKey);
        return ResponseEntity.ok(payment);
    }
    @PostMapping("/safe")
    @Operation(summary = "Process payment — Redis lock + DB idempotency + Outbox")
    public ResponseEntity<Payment> processPaymentSafe(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody PaymentRequest request) {
        Payment payment = paymentService.processPaymentSafe(
                request.getAccountId(), request.getAmount(), idempotencyKey);
        return ResponseEntity.ok(payment);
    }

}
