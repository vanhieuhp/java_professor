package hieunv.dev.netflixstack.payment.controller;

import hieunv.dev.netflixstack.payment.dto.request.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.response.CreatePaymentResponse;
import hieunv.dev.netflixstack.payment.dto.response.StoredResponse;
import hieunv.dev.netflixstack.payment.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<CreatePaymentResponse> createPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {

        StoredResponse stored = paymentService.createPayment(idempotencyKey, request);
        return ResponseEntity.status(HttpStatus.valueOf(stored.getHttpStatus())).body(stored.getBody());
    }
}
