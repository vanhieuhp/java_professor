package dev.hieunv.controller;

import dev.hieunv.domain.dto.payment.PaymentRequest;
import dev.hieunv.utils.ChecksumUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private static final String SECRET_KEY = "hieu.dev";

    @PostMapping("/pay")
    public ResponseEntity<String> processPayment(@RequestBody PaymentRequest request) {
        // Create a string to sign (important: consistent order of fields!)
        String data = request.orderId() + "|" + request.customerId() + "|" + request.amount();

        // Generate checksum on server side
        String expectedChecksum = ChecksumUtils.generateChecksum(data, SECRET_KEY);

        // Validate
        if (!expectedChecksum.equals(request.checksum())) {
            return ResponseEntity.badRequest().body("Invalid checksum!");
        }

        return ResponseEntity.ok("Payment processed successfully for order " + request.orderId());
    }


}


