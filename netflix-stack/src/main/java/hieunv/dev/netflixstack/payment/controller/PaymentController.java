package hieunv.dev.netflixstack.payment.controller;

import hieunv.dev.netflixstack.payment.PaymentService;
import hieunv.dev.netflixstack.payment.dto.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.CreatePaymentResponse;
import hieunv.dev.netflixstack.payment.dto.StoredResponse;
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
        return ResponseEntity.status(HttpStatus.valueOf(stored.httpStatus())).body(stored.body());
    }
}
