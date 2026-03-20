package dev.hieunv.dataintensive.reliability.controller.reliability;

import dev.hieunv.dataintensive.reliability.client.BankClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private final BankClient bankClient;

    public PaymentController(BankClient bankClient) {
        this.bankClient = bankClient;
    }

    @PostMapping
    public Mono<ResponseEntity<String>> pay(@RequestBody Map<String, Object> req) {
        String cardId = (String) req.get("cardId");
        double amount = Double.parseDouble(req.get("amount").toString());

        return bankClient.chargeCard(cardId, amount)
                .map(result -> ResponseEntity.ok("Payment result: " + result));
    }
}