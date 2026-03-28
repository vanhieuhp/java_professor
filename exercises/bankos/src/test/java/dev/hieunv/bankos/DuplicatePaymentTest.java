package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.repository.PaymentRepository;
import dev.hieunv.bankos.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest
public class DuplicatePaymentTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(
                new Account("Alice", new BigDecimal("1000.00")));
        accountId = account.getId();
    }

    @Test
    void demonstrateDuplicatePayment() {
        System.out.println("=== DUPLICATE PAYMENT DEMO ===");
        System.out.println("Initial balance: $1000.00");
        System.out.println("Customer pays $500 — network times out — client retries\n");

        // First request — succeeds but response is lost
        Payment first = paymentService.processPayment(
                accountId, new BigDecimal("500.00"));
        System.out.println("[Request 1] Payment ID: " + first.getId());

        // Retry — server has no memory of the first request
        Payment second = paymentService.processPayment(
                accountId, new BigDecimal("500.00"));
        System.out.println("[Request 2] Payment ID: " + second.getId());

        BigDecimal finalBalance = accountRepository
                .findById(accountId).get().getBalance();
        long paymentCount = paymentRepository.count();

        System.out.println("\nPayment records in DB: " + paymentCount);
        System.out.println("Final balance: $" + finalBalance);

        if (paymentCount > 1) {
            System.out.println("CUSTOMER CHARGED TWICE!");
        }
    }

    @Test
    void demonstrateIdempotencyFix() {
        System.out.println("=== IDEMPOTENCY FIX DEMO ===");
        System.out.println("Initial balance: $1000.00");
        System.out.println("Customer pays $500 — network times out — client retries\n");

        // Client generates ONE key per payment attempt — same key on retry
        String idempotencyKey = UUID.randomUUID().toString();
        System.out.println("Idempotency key: " + idempotencyKey);

        // First request
        Payment first = paymentService.processPaymentIdempotent(
                accountId, new BigDecimal("500.00"), idempotencyKey);
        System.out.println("[Request 1] Payment ID: " + first.getId());

        // Retry — same key
        Payment second = paymentService.processPaymentIdempotent(
                accountId, new BigDecimal("500.00"), idempotencyKey);
        System.out.println("[Request 2] Payment ID: " + second.getId());

        BigDecimal finalBalance = accountRepository
                .findById(accountId).get().getBalance();
        long paymentCount = paymentRepository.count();

        System.out.println("\nPayment records in DB: " + paymentCount);
        System.out.println("Final balance:         $" + finalBalance);

        // ── Assertions ───────────────────────────────────────────

        // 1. Only ONE payment record — retry was detected
        assertThat(paymentCount).isEqualTo(1);

        // 2. Both calls returned the SAME payment ID — cached response
        assertThat(first.getId()).isEqualTo(second.getId());

        // 3. Balance deducted only ONCE — $1000 - $500 = $500
        assertThat(finalBalance).isEqualByComparingTo("500.00");

        // 4. Idempotency key stored correctly
        assertThat(first.getIdempotencyKey()).isEqualTo(idempotencyKey);
    }
}
