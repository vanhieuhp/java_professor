package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.dto.payment.PaymentGatewayRequest;
import dev.hieunv.bankos.dto.payment.PaymentGatewayResponse;
import dev.hieunv.bankos.exception.PaymentBusinessException;
import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.model.IdempotencyKey;
import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.repository.IdempotencyKeyRepository;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import dev.hieunv.bankos.repository.PaymentRepository;
import dev.hieunv.bankos.service.PaymentService;
import dev.hieunv.bankos.service.RedisIdempotencyGuard;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AccountRepository accountRepository;
    private final OutboxEventRepository outboxEventRepository;  // ← new
    private final ObjectMapper objectMapper;                    // ← new
    private final RedisIdempotencyGuard redisGuard;  // ← new

    @Transactional
    @Override
    public Payment processPayment(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + accountId));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));
        Payment payment = paymentRepository.save(new Payment(accountId, amount));

        System.out.println("[Payment] Processed $" + amount
                + " from Account " + accountId
                + " → Payment ID: " + payment.getId()
                + " → balance: $" + account.getBalance());

        return payment;
    }

    @Transactional
    @Override
    public Payment processPaymentIdempotent(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Check if we already processed this exact request
        Optional<Payment> existing =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            System.out.println("[Payment] Duplicate detected for key: "
                    + idempotencyKey
                    + " → returning cached Payment ID: "
                    + existing.get().getId());
            return existing.get(); // return original result — do NOT reprocess
        }

        // 2. New request — process it
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + accountId));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));

        // 3. Save payment + idempotency key in ONE transaction
        //    If this crashes halfway, both roll back together — atomic!
        Payment payment = paymentRepository.save(
                new Payment(accountId, amount, idempotencyKey));

        idempotencyKeyRepository.save(
                new IdempotencyKey(idempotencyKey, payment.getId()));

        System.out.println("[Payment] New payment processed for key: "
                + idempotencyKey
                + " → Payment ID: " + payment.getId()
                + " → balance: $" + account.getBalance());

        return payment;
    }

    @Transactional
    @Override
    public Payment insertPendingPayment(Long accountId, BigDecimal amount) {
        String key = UUID.randomUUID().toString();
        Payment payment = Payment.builder()
                .accountId(accountId)
                .amount(amount)
                .idempotencyKey(key)
                .status("PENDING")
                .build();
        paymentRepository.save(payment);
        System.out.println("[Payment] Inserted PENDING $" + amount
                + " for Account " + accountId
                + " → Payment ID: " + payment.getId());
        return payment;
    }

    @Bulkhead(name = "paymentGateway")
    @CircuitBreaker(name = "paymentGateway", fallbackMethod = "gatewayFallback")
    @Retry(name = "paymentGateway")
    @Override
    public PaymentGatewayResponse callGatewayWithResilience(PaymentGatewayRequest request) {
        log.info("[Gateway] Calling external gateway account={} amount={}",
                request.getAccountId(), request.getAmount());

        simulateExternalGateway(request);

        return PaymentGatewayResponse.builder()
                .status("SUCCESS")
                .gatewayRef("GW-" + System.currentTimeMillis())
                .message("Payment processed")
                .build();
    }

    // Fallback — called by all three annotations when they trigger
    // Java 25: pattern matching in switch is fully stable (no --enable-preview needed)
    @Override
    public PaymentGatewayResponse gatewayFallback(PaymentGatewayRequest request, Throwable t) {
        String reason = switch (t) {
            case BulkheadFullException e      -> "TOO_MANY_REQUESTS";
            case CallNotPermittedException e  -> "CIRCUIT_OPEN";
            case PaymentBusinessException e   -> "BUSINESS_ERROR: " + e.getMessage();
            default                           -> "GATEWAY_UNAVAILABLE";
        };

        log.warn("[Gateway] Fallback triggered account={} reason={}",
                request.getAccountId(), reason);

        return PaymentGatewayResponse.builder()
                .status("FAILED")
                .message(reason)
                .build();
    }

    // Simulates a real external HTTP call:
    //   - amount > 9000 → PaymentBusinessException (NOT retried — business rule)
    //   - normal        → 300ms latency (slow but succeeds)
    private void simulateExternalGateway(PaymentGatewayRequest request) {
        if (request.getAmount().compareTo(new BigDecimal("9000")) > 0) {
            throw new PaymentBusinessException("Amount exceeds gateway limit");
        }

        // k6 can force failures by setting simulateFailure=true
        if (request.isSimulateFailure()) {
            throw new RuntimeException("Simulated gateway failure");
        }

        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Gateway call interrupted");
        }
    }

    @Transactional
    @Override
    public Payment processPaymentWithOutbox(Long accountId, BigDecimal amount, String idempotencyKey) {
        // Step 1 — idempotency check (same as before)
        Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.info("[Outbox] Duplicate key={} → cached ID: {}",
                    idempotencyKey, existing.get().getId());
            return existing.get();
        }

        // Step 2 — deduct balance
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + accountId));
        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }
        account.setBalance(account.getBalance().subtract(amount));

        // Step 3 — save payment
        Payment payment = paymentRepository.save(new Payment(accountId, amount, idempotencyKey));

        // Step 4 — write outbox event IN THE SAME TRANSACTION
        // This is the critical difference from dual-write
        // If this line throws → entire transaction rolls back including Step 3
        OutboxEvent event = buildOutboxEvent(payment);
        outboxEventRepository.save(event);

        log.info("[Outbox] Payment ID={} + OutboxEvent saved atomically", payment.getId());

        // transaction commits here — both payment AND outbox event are durable
        return payment;
    }

    private OutboxEvent buildOutboxEvent(Payment payment) {
        try {
            String payload = objectMapper.writeValueAsString(Map.of(
                    "paymentId", payment.getId(),
                    "accountId", payment.getAccountId(),
                    "amount",    payment.getAmount().toString(),
                    "status",    "PROCESSED",
                    "timestamp", LocalDateTime.now().toString()
            ));
            return OutboxEvent.builder()
                    .aggregateType("Payment")
                    .aggregateId(payment.getId())
                    .eventType("PAYMENT_PROCESSED")
                    .payload(payload)
                    .status("PENDING")
                    .createdAt(LocalDateTime.now())
                    .retryCount(0)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox event", e);
        }
    }

    @Override
    public Payment processPaymentSafe(Long accountId, BigDecimal amount, String idempotencyKey) {
        // Layer 1: Redis lock
        boolean locked = redisGuard.tryAcquire(idempotencyKey);
        if (!locked) {
            // Another thread is currently processing this exact key
            // Wait briefly then check if it completed
            log.info("[PaymentSafe] Key={} locked by another thread — checking DB",
                    idempotencyKey);
            return waitAndReturn(idempotencyKey);
        }

        try {
            // ── Layer 2: DB idempotency + Outbox (one transaction) ────────
            // processPaymentWithOutbox already handles:
            //   - idempotency key check
            //   - balance deduction
            //   - payment save
            //   - outbox event save
            // all in ONE @Transactional
            return processPaymentWithOutbox(accountId, amount, idempotencyKey);

        } catch (DataIntegrityViolationException e) {
            // ── Layer 3: DB unique constraint caught a duplicate ──────────
            // This means Redis lock failed (TTL expired, Redis was down)
            // but DB unique constraint saved us — correct behavior
            log.warn("[PaymentSafe] DB unique constraint caught duplicate key={}",
                    idempotencyKey);
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException(
                            "Duplicate key but payment not found: " + idempotencyKey));

        } finally {
            // Always release lock — even if processing failed
            // TTL is the backup if this line is never reached (crash)
            redisGuard.release(idempotencyKey);
        }
    }

    // If Redis lock was held by another thread, wait for it to finish
    // then return the payment it created
    private Payment waitAndReturn(String idempotencyKey) {
        int attempts = 0;
        while (attempts < 10) {
            try {
                Thread.sleep(100);  // wait 100ms per attempt → max 1s total
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            // Check if the other thread finished
            if (!redisGuard.isLocked(idempotencyKey)) {
                return paymentRepository.findByIdempotencyKey(idempotencyKey)
                        .orElseThrow(() -> new RuntimeException(
                                "Lock released but payment not found: " + idempotencyKey));
            }
            attempts++;
        }
        throw new RuntimeException("Timeout waiting for payment: " + idempotencyKey);
    }
}
