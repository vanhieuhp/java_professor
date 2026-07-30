# Transactional Outbox Pattern + 3-Layer Idempotency — Teaching Guide

> **Purpose**: This document is a self-contained, agent-readable blueprint.
> An AI agent or developer can follow this guide to implement the same strategy
> for **any business domain** (orders, subscriptions, invoices, etc.).
> All examples come from the `bankos` reference implementation.

---

## Table of Contents

1. [Strategy Overview](#1-strategy-overview)
2. [When to Use This Pattern](#2-when-to-use-this-pattern)
3. [Architecture Diagram](#3-architecture-diagram)
4. [Folder Structure](#4-folder-structure)
5. [Data Models](#5-data-models)
6. [Repositories](#6-repositories)
7. [Core Service: processPaymentWithOutbox](#7-core-service-processpaymentwithoutbox)
8. [3-Layer Idempotency Guard](#8-3-layer-idempotency-guard)
9. [Outbox Relay — Polling Scheduler](#9-outbox-relay--polling-scheduler)
10. [Outbox Event Processor](#10-outbox-event-processor)
11. [Kafka Producer](#11-kafka-producer)
12. [Kafka Consumer with Deduplication](#12-kafka-consumer-with-deduplication)
13. [Redis Idempotency Guard Component](#13-redis-idempotency-guard-component)
14. [DTOs](#14-dtos)
15. [Configuration](#15-configuration)
16. [End-to-End Flow Walkthrough](#16-end-to-end-flow-walkthrough)
17. [Failure Scenarios and How Each Layer Handles Them](#17-failure-scenarios-and-how-each-layer-handles-them)
18. [How to Adapt to Another Business Domain](#18-how-to-adapt-to-another-business-domain)

---

## 1. Strategy Overview

This pattern solves **three distributed-systems problems simultaneously**:

| Problem | Solution Used |
|---|---|
| Message lost when DB commit succeeds but Kafka publish fails | **Transactional Outbox** — publish happens after DB commit |
| Duplicate request processed twice (retry / network replay) | **3-Layer Idempotency** (Redis lock → DB key → unique constraint) |
| Thundering herd of concurrent duplicate requests | **Redis distributed lock** with TTL and wait-and-return |

**Core principle — the Transactional Outbox:**

Instead of calling Kafka directly inside a `@Transactional` method (which can leave DB and Kafka out of sync), you write an `outbox_events` row **in the same database transaction** as your business data. A separate, decoupled scheduler then reads PENDING events and publishes them to Kafka. If the publish fails, the event stays PENDING and will be retried.

```
[HTTP Request]
     │
     ▼
[Service @Transactional]
     ├─ write business row (e.g. Payment)
     └─ write OutboxEvent row (PENDING)   ← ATOMIC with above
          │
          │  (same DB transaction committed here)
          │
     [Outbox Relay @Scheduled every 2s]
          │
          ▼
     [Kafka Produce]
          │
          ▼
     [Consumers] ── audit, fraud, notification, stats …
```

---

## 2. When to Use This Pattern

Use this pattern when **all** of the following are true:

- You need **at-least-once delivery** of domain events to Kafka (or any message broker).
- Your business operation is a **database write** (payment, order, transfer).
- Duplicate HTTP requests (retries from clients) must be **safe to replay** — same key, same result.
- Multiple **downstream systems** need to react to the same event independently (fan-out).

**Do NOT use** if:
- Your operation is read-only.
- You can tolerate eventual message loss.
- You're on a queue that supports distributed transactions natively (e.g., JMS/XA).

---

## 3. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         HTTP Request Layer                              │
│  POST /api/payments/safe {accountId, amount, idempotencyKey}            │
└────────────────────────────────┬────────────────────────────────────────┘
                                 │
                    ┌────────────▼────────────┐
                    │   processPaymentSafe     │  Layer 1: Redis Lock
                    │  (PaymentServiceImpl)    │  tryAcquire(idempotencyKey)
                    └────────────┬────────────┘
                                 │ locked
                    ┌────────────▼────────────┐
                    │  processPaymentWithOutbox│  @Transactional
                    │  (PaymentServiceImpl)    │
                    │                         │  Layer 2: DB idempotency check
                    │  1. check idempotency    │  findByIdempotencyKey()
                    │  2. deduct balance       │  pessimistic lock on Account
                    │  3. save Payment         │  ┐
                    │  4. save OutboxEvent     │  ┘ ONE ATOMIC COMMIT
                    └────────────┬────────────┘
                    Layer 3: DB UNIQUE constraint on idempotency_key
                                 │
┌─────────────────────────────────────────────────────────────────────────┐
│                    PostgreSQL (same transaction)                         │
│  ┌─────────────┐    ┌──────────────────┐    ┌────────────────────────┐ │
│  │  payments   │    │  outbox_events   │    │  accounts (locked row) │ │
│  │  (new row)  │    │  status=PENDING  │    │  balance -= amount     │ │
│  └─────────────┘    └──────────────────┘    └────────────────────────┘ │
└──────────────────────────────────┬──────────────────────────────────────┘
                                   │
              ┌────────────────────▼─────────────────────┐
              │  OutboxRelayService @Scheduled(2000ms)    │
              │  Polls PENDING rows → OutboxEventProcessor│
              └────────────────────┬─────────────────────┘
                                   │
              ┌────────────────────▼─────────────────────┐
              │          Apache Kafka                     │
              │  ┌─────────────────┐  ┌───────────────┐  │
              │  │ payment-events  │  │ wallet-status │  │
              │  └────────┬────────┘  └──────┬────────┘  │
              └───────────┼──────────────────┼───────────┘
                          │                  │
        ┌─────────────────▼──┐    ┌──────────▼──────────────────────┐
        │ PaymentEventConsumer│    │   WalletStatus downstream       │
        │ AuditConsumer       │    │   (balance read-model updater)  │
        │ FraudDetectionConsumer   └─────────────────────────────────┘
        │ NotificationConsumer│
        │ PaymentStatsConsumer│
        │ PaymentDltConsumer  │
        └─────────────────────┘
```

---

## 4. Folder Structure

```
src/main/java/dev/hieunv/bankos/
│
├── config/
│   ├── RedisConfig.java              # RedisTemplate + CaffeineCacheManager
│   ├── ObjectMapperConfig.java       # Jackson ObjectMapper bean
│   └── ThreadPoolConfig.java         # Async executor config
│
├── controller/
│   └── PaymentController.java        # REST endpoints
│
├── dto/
│   ├── payment/
│   │   ├── PaymentRequest.java           # Inbound: {accountId, amount}
│   │   ├── PaymentGatewayRequest.java    # Inbound: {accountId, amount, idempotencyKey, simulateFailure}
│   │   ├── PaymentGatewayResponse.java   # Outbound: {status, gatewayRef, message}
│   │   ├── PaymentProcessedEvent.java    # Kafka event DTO → "payment-events" topic
│   │   ├── PaymentCompletedEvent.java    # Kafka event DTO
│   │   ├── PaymentFailedEvent.java       # Kafka event DTO → DLT handling
│   │   └── PaymentStatEvent.java         # Stats consumer DTO
│   └── wallet/
│       └── WalletStatusEvent.java        # Kafka event DTO → "wallet-status" topic
│
├── enums/
│   ├── OutboxEventStatus.java        # PENDING, PUBLISHED, FAILED
│   ├── PaymentStatus.java            # PENDING, COMPLETED, FAILED
│   └── GatewayStatus.java            # SUCCESS, FAILED
│
├── exception/
│   └── PaymentBusinessException.java # Non-retryable business error
│
├── model/
│   ├── Account.java                  # accounts table — has balance
│   ├── Payment.java                  # payments table — the business entity
│   ├── OutboxEvent.java              # outbox_events table — relay queue
│   └── IdempotencyKey.java           # idempotency_keys table — processed tracking
│
├── repository/
│   ├── AccountRepository.java        # findByIdWithLock (pessimistic)
│   ├── PaymentRepository.java        # findByIdempotencyKey
│   ├── OutboxEventRepository.java    # findPendingEvents (top 10)
│   └── IdempotencyKeyRepository.java
│
└── service/
    ├── PaymentService.java           # Interface
    │
    ├── impl/
    │   └── PaymentServiceImpl.java   # Core business logic
    │
    ├── component/
    │   └── RedisIdempotencyGuard.java  # Redis distributed lock helper
    │
    ├── outbox/
    │   ├── OutboxRelayService.java   # @Scheduled poller
    │   └── OutboxEventProcessor.java # Per-event: publish → update status
    │
    ├── producer/
    │   ├── PaymentEventProducer.java   # Publishes to "payment-events"
    │   └── WalletStatusProducer.java   # Publishes to "wallet-status"
    │
    └── consumer/
        ├── PaymentEventConsumer.java   # Main consumer (idempotent)
        ├── AuditConsumer.java
        ├── FraudDetectionConsumer.java
        ├── NotificationConsumer.java
        ├── PaymentStatsConsumer.java
        ├── PaymentDltConsumer.java     # Dead Letter Topic handler
        └── PaymentSagaConsumer.java
```

---

## 5. Data Models

### 5.1 OutboxEvent — the relay queue row

```java
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String aggregateType;  // "Payment", "Order", "Transfer"
    private Long   aggregateId;    // FK to the business entity
    private String eventType;      // "PAYMENT_PROCESSED", "ORDER_CREATED"

    @Column(columnDefinition = "TEXT")
    private String payload;        // JSON blob of the event data

    @Enumerated(EnumType.STRING)
    private OutboxEventStatus status; // PENDING → PUBLISHED | FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt; // set when successfully published
    private int retryCount;            // incremented on each failed publish attempt
}
```

### 5.2 Payment — the business entity

```java
@Entity
@Table(name = "payments")
public class Payment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long       accountId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;  // PENDING, COMPLETED, FAILED

    private LocalDateTime createdAt;

    @Column(unique = true)         // ← Layer 3 idempotency: DB unique constraint
    private String idempotencyKey;
}
```

### 5.3 IdempotencyKey — processed tracking

```java
@Entity
@Table(name = "idempotency_keys")
public class IdempotencyKey {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true)
    private String idempotencyKey;

    private Long          paymentId;
    private LocalDateTime processedAt;
}
```

### 5.4 Account — the mutable state

```java
@Entity
@Table(name = "accounts")
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String     owner;
    private BigDecimal balance;
}
```

---

## 6. Repositories

### OutboxEventRepository — the most critical query

```java
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // Fetch the next batch of unprocessed events in creation order
    // Limit=10 prevents the relay from being overwhelmed
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'PENDING' " +
           "ORDER BY e.createdAt ASC")
    @QueryHints(@QueryHint(name = "jakarta.persistence.query.timeout", value = "5000"))
    List<OutboxEvent> findPendingEvents(Pageable pageable);
    // Call as: findPendingEvents(PageRequest.of(0, 10))

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String type, Long id);
}
```

### AccountRepository — pessimistic lock for balance safety

```java
public interface AccountRepository extends JpaRepository<Account, Long> {

    // PESSIMISTIC_WRITE locks the row until the transaction commits
    // Prevents two concurrent threads from both reading $100 and both subtracting $50
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdWithLock(@Param("id") Long id);
}
```

### PaymentRepository — idempotency check

```java
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);
}
```

---

## 7. Core Service: processPaymentWithOutbox

This is the **atomic heart** of the pattern. Everything in this method must commit or rollback together.

```java
@Transactional
@Override
public Payment processPaymentWithOutbox(Long accountId, BigDecimal amount, String idempotencyKey) {

    // ── Step 1: Idempotency check ─────────────────────────────────────────
    // If we already processed this key, return the original result immediately.
    // This handles: client retries, load-balancer replays, duplicate HTTP calls.
    Optional<Payment> existing = paymentRepository.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        log.info("[Outbox] Duplicate key={} → cached ID: {}", idempotencyKey, existing.get().getId());
        return existing.get();
    }

    // ── Step 2: Deduct balance under pessimistic lock ─────────────────────
    // PESSIMISTIC_WRITE ensures no other transaction can read/write this account row
    // until we commit. Prevents double-spend on concurrent requests.
    Account account = accountRepository.findByIdWithLock(accountId)
            .orElseThrow(() -> new EntityNotFoundException("Account not found: " + accountId));

    if (account.getBalance().compareTo(amount) < 0) {
        throw new IllegalStateException("Insufficient funds!");
    }
    account.setBalance(account.getBalance().subtract(amount));

    // ── Step 3: Persist the Payment ──────────────────────────────────────
    Payment payment = paymentRepository.save(new Payment(accountId, amount, idempotencyKey));

    // ── Step 4: Write the Outbox event IN THE SAME TRANSACTION ───────────
    // This is the key invariant: if the payment row exists, the outbox row exists.
    // If this transaction rolls back, BOTH rows disappear — no orphaned events.
    OutboxEvent event = buildOutboxEvent(payment, account);
    outboxEventRepository.save(event);

    return payment;
    // ← @Transactional commits here: payment + outbox_event written atomically
}

private OutboxEvent buildOutboxEvent(Payment payment, Account account) {
    try {
        String payload = objectMapper.writeValueAsString(Map.of(
            "paymentId",    payment.getId(),
            "accountId",    payment.getAccountId(),
            "amount",       payment.getAmount().toString(),
            "status",       "PROCESSED",
            "timestamp",    LocalDateTime.now().toString(),
            "balanceAfter", account.getBalance().toString()
        ));
        return OutboxEvent.builder()
                .aggregateType("Payment")
                .aggregateId(payment.getId())
                .eventType("PAYMENT_PROCESSED")
                .payload(payload)
                .status(OutboxEventStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .retryCount(0)
                .build();
    } catch (Exception e) {
        throw new RuntimeException("Failed to serialize outbox event", e);
    }
}
```

**Why this is safe:**
- If the JVM crashes after the DB commit → outbox row is PENDING → relay will publish it later.
- If Kafka is down → outbox row stays PENDING → relay retries every 2 seconds.
- If the DB commit itself fails → neither row exists → no event published → clean state.

---

## 8. 3-Layer Idempotency Guard

The full `processPaymentSafe` wraps `processPaymentWithOutbox` with two additional protection layers:

```
Layer 1: Redis distributed lock (speed — stops duplicates before hitting DB)
     │
Layer 2: DB idempotency check (inside processPaymentWithOutbox — correctness)
     │
Layer 3: DB UNIQUE constraint on idempotency_key (last resort — catches Redis failures)
```

```java
@Override
public Payment processPaymentSafe(Long accountId, BigDecimal amount, String idempotencyKey) {

    // ── Layer 1: Redis lock ───────────────────────────────────────────────
    // Atomic SET NX PX — only one thread wins the lock per idempotency key.
    // TTL = 30s: if the process crashes, the lock auto-expires.
    boolean locked = redisGuard.tryAcquire(idempotencyKey);

    if (!locked) {
        // Another thread is processing this exact key RIGHT NOW.
        // Wait up to 1 second for it to finish, then return its result.
        log.info("[PaymentSafe] Key={} locked by another thread — checking DB", idempotencyKey);
        return waitAndReturn(idempotencyKey);
    }

    try {
        // ── Layer 2: DB idempotency + Outbox (one transaction) ───────────
        return processPaymentWithOutbox(accountId, amount, idempotencyKey);

    } catch (DataIntegrityViolationException e) {
        // ── Layer 3: DB unique constraint caught a duplicate ─────────────
        // Redis lock failed (TTL expired, Redis was down) but DB saved us.
        log.warn("[PaymentSafe] DB unique constraint caught duplicate key={}", idempotencyKey);
        return paymentRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new RuntimeException(
                        "Duplicate key but payment not found: " + idempotencyKey));
    } finally {
        // Always release the lock — even if processing failed.
        // The TTL is a backup in case this line is never reached (crash).
        redisGuard.release(idempotencyKey);
    }
}

private Payment waitAndReturn(String idempotencyKey) {
    for (int i = 0; i < 10; i++) {
        try { Thread.sleep(100); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
        }
        if (!redisGuard.isLocked(idempotencyKey)) {
            return paymentRepository.findByIdempotencyKey(idempotencyKey)
                    .orElseThrow(() -> new RuntimeException(
                            "Lock released but payment not found: " + idempotencyKey));
        }
    }
    throw new RuntimeException("Timeout waiting for payment: " + idempotencyKey);
}
```

---

## 9. Outbox Relay — Polling Scheduler

The relay bridges the database and Kafka. It runs on a **fixed-delay schedule** (not fixed-rate) to prevent overlapping executions.

```java
@Service
@RequiredArgsConstructor
@EnableScheduling
@Slf4j
public class OutboxRelayService {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxEventProcessor  outboxEventProcessor;

    // Runs every 2000ms AFTER the previous execution completes.
    // Fixed-delay (not fixed-rate) prevents pile-up if processing is slow.
    @Scheduled(fixedDelay = 2000)
    public void relay() {
        // Fetch at most 10 PENDING events at a time — prevents memory pressure
        List<OutboxEvent> pending = outboxEventRepository
                .findPendingEvents(PageRequest.of(0, 10));

        if (pending.isEmpty()) return;

        log.info("[OutboxRelay] Processing {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            outboxEventProcessor.processEvent(event);
        }
    }
}
```

**Production note:** In a production system with high throughput, replace this scheduler with **Debezium CDC** (Change Data Capture). Debezium tails the PostgreSQL WAL (Write-Ahead Log) and streams new `outbox_events` rows to Kafka in near-real-time, with no polling overhead. The polling scheduler is simpler to set up and works well for moderate volumes.

---

## 10. Outbox Event Processor

Each event is processed independently. Failures increment the retry counter; after 3 failures the event is marked FAILED and excluded from future polls.

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {

    private final PaymentEventProducer  paymentEventProducer;
    private final WalletStatusProducer  walletStatusProducer;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper          objectMapper;

    @Transactional
    public void processEvent(OutboxEvent event) {
        try {
            // Deserialize the JSON payload stored in the outbox row
            Map<String, Object> payload = objectMapper.readValue(
                    event.getPayload(), new TypeReference<>() {});

            // Publish to all relevant Kafka topics
            publishToKafka(event, payload);

            // Mark as published — this DB write and the Kafka send are NOT in the same
            // transaction, so exactly-once is not guaranteed. The consumer must deduplicate.
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);

            log.info("[OutboxProcessor] Published event id={} type={}", event.getId(), event.getEventType());

        } catch (Exception e) {
            // Increment retry count; mark as FAILED after 3 attempts
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= 3) {
                event.setStatus(OutboxEventStatus.FAILED);
                log.error("[OutboxProcessor] Event id={} permanently FAILED after 3 retries", event.getId());
            }
            outboxEventRepository.save(event);
        }
    }

    private void publishToKafka(OutboxEvent event, Map<String, Object> payload) {
        if ("PAYMENT_PROCESSED".equals(event.getEventType())) {
            Long   accountId = Long.valueOf(payload.get("accountId").toString());
            Long   paymentId = Long.valueOf(payload.get("paymentId").toString());
            String amount    = payload.get("amount").toString();

            // Fan-out: publish to multiple topics from one outbox event
            paymentEventProducer.send(PaymentProcessedEvent.builder()
                    .paymentId(paymentId)
                    .accountId(accountId)
                    .amount(new BigDecimal(amount))
                    .build());

            walletStatusProducer.send(WalletStatusEvent.builder()
                    .accountId(accountId)
                    .balance(new BigDecimal(payload.get("balanceAfter").toString()))
                    .status("ACTIVE")
                    .occurredAt(LocalDateTime.now())
                    .build());
        }
    }
}
```

---

## 11. Kafka Producer

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {

    private final KafkaTemplate<String, PaymentProcessedEvent> kafkaTemplate;

    private static final String TOPIC = "payment-events";

    public void send(PaymentProcessedEvent event) {
        // Key = accountId string → guarantees ordering within a partition for the same account
        String key = event.getAccountId().toString();

        kafkaTemplate.send(TOPIC, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Producer] Failed to send payment event key={}: {}", key, ex.getMessage());
                        throw new RuntimeException("Kafka send failed", ex);
                    }
                    log.info("[Producer] Sent payment event key={} offset={}",
                            key, result.getRecordMetadata().offset());
                });
    }
}
```

**Kafka producer configuration (application.yml):**

```yaml
spring:
  kafka:
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all          # wait for leader + all replicas to acknowledge
      retries: 3         # retry on transient send failures
      linger.ms: 0       # send immediately (no batching delay)
      properties:
        enable.idempotence: true  # Kafka producer-level deduplication
```

---

## 12. Kafka Consumer with Deduplication

The consumer must be **idempotent** because the outbox relay provides **at-least-once** delivery (Kafka can replay messages on rebalance, restart, or retry).

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final StringRedisTemplate redisTemplate;

    private static final String TOPIC    = "payment-events";
    private static final String GROUP_ID = "bankos-payment-group";

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(ConsumerRecord<String, PaymentProcessedEvent> record) {
        PaymentProcessedEvent event = record.value();
        String dedupeKey = "processed:payment:" + event.getPaymentId();

        // Redis SET NX with 24-hour TTL — idempotent deduplication
        // setIfAbsent returns true only on first processing; false means already handled
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(dedupeKey, "1", Duration.ofHours(24));

        if (Boolean.FALSE.equals(isNew)) {
            log.info("[Consumer] Skipping duplicate paymentId={}", event.getPaymentId());
            return;
        }

        // Simulate business logic failure for amounts > 9000 → triggers DLT
        if (event.getAmount().compareTo(new BigDecimal("9000")) > 0) {
            throw new RuntimeException("Amount exceeds consumer processing limit");
        }

        log.info("[Consumer] Processing paymentId={} accountId={} amount={}",
                event.getPaymentId(), event.getAccountId(), event.getAmount());

        // Your downstream business logic here:
        // e.g., update ledger, trigger notification, update read model
    }
}
```

**Consumer configuration:**

```yaml
spring:
  kafka:
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: false   # manual ack — only commit offset after success
    listener:
      ack-mode: RECORD            # commit after each record, not batch
```

---

## 13. Redis Idempotency Guard Component

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyGuard {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:payment:";
    private static final long   LOCK_TTL_SECONDS = 30;

    /**
     * Attempts to acquire a distributed lock for the given key.
     * Uses SET key value NX PX — atomic, non-blocking.
     * Returns true if this caller won the lock; false if another thread holds it.
     */
    public boolean tryAcquire(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(LOCK_TTL_SECONDS));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * Releases the lock. Called in finally block to ensure cleanup.
     */
    public void release(String idempotencyKey) {
        redisTemplate.delete(LOCK_PREFIX + idempotencyKey);
    }

    /**
     * Checks if a lock exists (used by waitAndReturn polling loop).
     */
    public boolean isLocked(String idempotencyKey) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(LOCK_PREFIX + idempotencyKey));
    }
}
```

---

## 14. DTOs

### Inbound (HTTP request)

```java
// PaymentRequest.java — simple endpoint
public record PaymentRequest(Long accountId, BigDecimal amount) {}

// PaymentGatewayRequest.java — resilience4j + outbox endpoint
@Data @Builder
public class PaymentGatewayRequest {
    private Long       accountId;
    private BigDecimal amount;
    private String     idempotencyKey;
    private boolean    simulateFailure;
}
```

### Outbound (Kafka events)

```java
// PaymentProcessedEvent.java — published to "payment-events" topic
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentProcessedEvent {
    private Long       paymentId;
    private Long       accountId;
    private BigDecimal amount;
    private String     status;
    private LocalDateTime timestamp;
}

// WalletStatusEvent.java — published to "wallet-status" topic (compacted)
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class WalletStatusEvent {
    private Long          accountId;
    private BigDecimal    balance;
    private String        status;     // "ACTIVE", "FROZEN", "CLOSED"
    private LocalDateTime occurredAt;
}

// PaymentGatewayResponse.java — HTTP response
@Data @Builder
public class PaymentGatewayResponse {
    private GatewayStatus status;   // SUCCESS, FAILED
    private String        gatewayRef;
    private String        message;
}
```

---

## 15. Configuration

### application.yml (full relevant sections)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/bankos
    hikari:
      maximum-pool-size: 5
      connection-timeout: 3000

  jpa:
    hibernate:
      ddl-auto: update
    properties:
      jakarta.persistence.lock.timeout: 5000  # 5s before pessimistic lock times out

  kafka:
    bootstrap-servers: localhost:9092
    producer:
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      linger.ms: 0
    consumer:
      group-id: bankos-payment-group
      auto-offset-reset: earliest
      enable-auto-commit: false
    listener:
      ack-mode: RECORD

  data:
    redis:
      host: localhost
      port: 6379
      timeout: 2000ms

resilience4j:
  bulkhead:
    instances:
      paymentGateway:
        max-concurrent-calls: 5      # reject if >5 threads in gateway call
  circuitbreaker:
    instances:
      paymentGateway:
        failure-rate-threshold: 50   # open circuit at 50% failure rate
        sliding-window-size: 10
  retry:
    instances:
      paymentGateway:
        max-attempts: 3
        wait-duration: 200ms
        ignore-exceptions:
          - dev.hieunv.bankos.exception.PaymentBusinessException  # don't retry business errors
```

### Kafka Topics to create

```bash
# payment-events: standard topic, multi-partition for parallelism
kafka-topics.sh --create \
  --topic payment-events \
  --partitions 6 \
  --replication-factor 2 \
  --config retention.ms=604800000

# wallet-status: log-compacted topic — keeps only latest balance per accountId key
kafka-topics.sh --create \
  --topic wallet-status \
  --partitions 6 \
  --replication-factor 2 \
  --config cleanup.policy=compact \
  --config retention.ms=604800000

# Dead Letter Topic — created automatically by Spring Kafka on failure
# payment-events.DLT
```

---

## 16. End-to-End Flow Walkthrough

### Scenario: Client sends POST /api/payments/safe for the first time

```
1. HTTP POST arrives at PaymentController
   Body: { accountId: 42, amount: 500.00, idempotencyKey: "uuid-abc-123" }

2. PaymentController calls paymentService.processPaymentSafe(42, 500, "uuid-abc-123")

3. processPaymentSafe — Layer 1 Redis lock:
   Redis: SET lock:payment:uuid-abc-123 LOCKED NX PX 30000
   → returns true (lock acquired)

4. processPaymentSafe calls processPaymentWithOutbox(42, 500, "uuid-abc-123")

5. processPaymentWithOutbox opens @Transactional:
   a. SELECT * FROM payments WHERE idempotency_key = 'uuid-abc-123' → empty
   b. SELECT * FROM accounts WHERE id = 42 FOR UPDATE  ← row locked
   c. account.balance = 1000 - 500 = 500  (in memory)
   d. INSERT INTO payments (account_id, amount, idempotency_key, status)
      VALUES (42, 500, 'uuid-abc-123', 'COMPLETED')  → payment_id = 101
   e. INSERT INTO outbox_events (aggregate_type, aggregate_id, event_type, payload, status, retry_count)
      VALUES ('Payment', 101, 'PAYMENT_PROCESSED', '{"paymentId":101,...}', 'PENDING', 0)
   f. UPDATE accounts SET balance = 500 WHERE id = 42
   g. COMMIT  ← all 3 rows written atomically

6. processPaymentSafe finally block:
   Redis: DEL lock:payment:uuid-abc-123  ← lock released

7. HTTP response returned: Payment{id=101, status=COMPLETED}

8. OutboxRelayService @Scheduled fires (within 2 seconds):
   SELECT * FROM outbox_events WHERE status = 'PENDING' ORDER BY created_at LIMIT 10
   → returns [event_id=55, aggregate_id=101, event_type=PAYMENT_PROCESSED]

9. OutboxEventProcessor.processEvent(event_id=55):
   a. Deserialize payload JSON
   b. paymentEventProducer.send(PaymentProcessedEvent{paymentId=101, accountId=42, amount=500})
      → Kafka: payment-events topic, key="42", offset=1042
   c. walletStatusProducer.send(WalletStatusEvent{accountId=42, balance=500})
      → Kafka: wallet-status topic, key="42"
   d. UPDATE outbox_events SET status='PUBLISHED', published_at=now() WHERE id=55
   e. COMMIT

10. PaymentEventConsumer.consume(record):
    a. dedupeKey = "processed:payment:101"
    b. Redis: SET processed:payment:101 1 NX EX 86400 → true (first time)
    c. Process business logic (update ledger, trigger notification, etc.)
    d. Kafka offset committed

11. AuditConsumer, FraudDetectionConsumer, NotificationConsumer each independently
    consume from payment-events and process the same event.
```

### Scenario: Client retries with the SAME idempotencyKey

```
Steps 1-2: same as above.

3. Redis: SET lock:payment:uuid-abc-123 LOCKED NX PX 30000
   → returns false (lock already gone since first request completed)
   Wait, the key was deleted in step 6. So it returns true again.

4. processPaymentWithOutbox:
   a. SELECT * FROM payments WHERE idempotency_key = 'uuid-abc-123' → FOUND payment_id=101
   b. Return existing payment immediately — NO second deduction, NO second outbox event.

Response: same Payment{id=101} returned. Client is safe to retry.
```

---

## 17. Failure Scenarios and How Each Layer Handles Them

| Scenario | What Happens | Layer That Saves You |
|---|---|---|
| Client retries same request | `findByIdempotencyKey` returns cached result | Layer 2 (DB check) |
| Two threads process same key concurrently | Redis lock: second thread waits, finds result in DB | Layer 1 (Redis lock) |
| Redis is down, two threads slip through | DB UNIQUE constraint on `idempotency_key` raises `DataIntegrityViolationException`; caught, returns existing | Layer 3 (DB constraint) |
| JVM crashes after DB commit but before Kafka send | `outbox_events` row is still PENDING; relay publishes it on next poll | Outbox Relay |
| Kafka is down | `outbox_events` stays PENDING; relay retries every 2s indefinitely | Outbox Relay |
| Kafka publish fails 3 times | Event marked FAILED; dead-letter handling / manual intervention needed | OutboxEventProcessor |
| Consumer receives duplicate message (Kafka replay) | `setIfAbsent` on Redis key returns false; message skipped | Consumer Redis dedup |
| Pessimistic lock timeout on Account | JPA throws `PessimisticLockingFailureException`; transaction rolls back; no data written | PostgreSQL row lock |
| Insufficient funds | `IllegalStateException` thrown before any DB write; transaction rolls back | Service validation |

---

## 18. How to Adapt to Another Business Domain

Follow this substitution table to apply the pattern to any business entity:

| bankos term | Your domain term | Notes |
|---|---|---|
| `Payment` | `Order`, `Subscription`, `Invoice`, `Transfer` | Your main business entity |
| `Account` | `Wallet`, `Inventory`, `CreditLimit` | The mutable state being protected |
| `PAYMENT_PROCESSED` | `ORDER_CREATED`, `SUBSCRIPTION_ACTIVATED` | Your event type string |
| `payment-events` | `order-events`, `subscription-events` | Kafka topic name |
| `wallet-status` | `inventory-snapshot`, `credit-snapshot` | Compacted read-model topic |
| `accountId` | `userId`, `merchantId`, `customerId` | Partition key for Kafka |
| `lock:payment:` | `lock:order:`, `lock:subscription:` | Redis key prefix |
| `processed:payment:` | `processed:order:`, `processed:invoice:` | Consumer dedup key prefix |

### Step-by-step checklist for a new domain

```
[ ] 1. Create your business entity table with a UNIQUE column: idempotency_key
[ ] 2. Create outbox_events table (copy schema from §5.1 — it's domain-agnostic)
[ ] 3. Write your core service method annotated @Transactional:
        - check idempotency key (findByIdempotencyKey equivalent)
        - apply business mutation (deduct, create, update)
        - save business entity
        - save OutboxEvent with domain-specific eventType and payload JSON
[ ] 4. Wrap with processXxxSafe using RedisIdempotencyGuard (copy §8)
[ ] 5. Copy OutboxRelayService as-is — change only the event type routing in processEvent
[ ] 6. Create a producer for your topic
[ ] 7. Create consumers — remember to add Redis deduplication (setIfAbsent pattern)
[ ] 8. Add Kafka topics to application.yml
[ ] 9. Add resilience4j config if your domain calls external gateways
```

### Minimal payload JSON contract

Every outbox event payload must include:

```json
{
  "entityId":   "<your-business-entity-id>",
  "accountId":  "<the-owner-identifier>",
  "amount":     "<decimal-string>",
  "status":     "PROCESSED",
  "timestamp":  "2024-01-01T12:00:00",
  "stateAfter": "<whatever-mutable-state-changed>"
}
```

The `OutboxEventProcessor` deserializes this map and routes by `eventType`. Add a new `if/else if` branch per new domain event type.

---

## Summary: The 4 Non-Negotiables

These four invariants must hold for the pattern to be correct:

1. **Business write + OutboxEvent write in ONE `@Transactional`** — never split them.
2. **DB UNIQUE constraint on `idempotency_key`** — without this, Layer 1 and Layer 2 have no safety net.
3. **Outbox relay polls and retries** — the relay must handle Kafka failures; business code must not.
4. **Consumer deduplicates** — the relay delivers at-least-once; consumers must be idempotent.

Break any of these and the guarantee collapses silently.
