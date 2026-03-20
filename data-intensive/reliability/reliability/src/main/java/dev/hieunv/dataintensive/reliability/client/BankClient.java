package dev.hieunv.dataintensive.reliability.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeoutException;

@Service
public class BankClient {

    private final WebClient webClient;

    public BankClient(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("http://localhost:8080/mock-bank").build();
    }

    @CircuitBreaker(name = "bankService", fallbackMethod = "fallback")
    @Retry(name = "bankService", fallbackMethod = "fallback")
    public Mono<String> chargeCard(String cardId, double amount) {
        return webClient.post()
                .uri("/charge")
                .bodyValue(Map.of("cardId", cardId, "amount", amount))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(
                        Duration.ofSeconds(2),
                        fallback(cardId, amount, new TimeoutException("Bank timeout after 2s"))
                ); // fail fast if >2s
    }

    // Fallback method
    private Mono<String> fallback(String cardId, double amount, Throwable ex) {
        System.out.println("Fallback triggered for cardId: " + cardId + ", amount: " + amount + ", due to: " + ex.getMessage());
        return Mono.just("BANK_FALLBACK: Could not process now (" + ex.getMessage() + ")");
    }
}
