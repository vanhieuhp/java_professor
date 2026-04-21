package dev.hieunv.bankos.service.consumer;
import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;

@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentEventConsumer {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String PROCESSED_KEY_PREFIX = "processed:payment:";
    private static final Duration PROCESSED_TTL = Duration.ofHours(24);

    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-payment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();

        log.info("[Consumer] Received paymentId={} accountId={} amount={} " +
                        "partition={} offset={}",
                event.getPaymentId(),
                event.getAccountId(),
                event.getAmount(),
                record.partition(),
                record.offset());

        // ── Idempotency check ─────────────────────────────────────────
        String redisKey = PROCESSED_KEY_PREFIX + event.getPaymentId();
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(redisKey, "PROCESSED", PROCESSED_TTL);
        if (!Boolean.TRUE.equals(isNew)) {
            log.info("[Consumer] Duplicate detected — skipping paymentId={}",
                    event.getPaymentId());
            return; // ← safe to skip, already processed
        }

        // ── Actual processing ─────────────────────────────────────────
        if (event.getAmount().compareTo(new BigDecimal("9000")) > 0) {
            // Clean up Redis key so retry can reacquire it
            redisTemplate.delete(redisKey);
            log.warn("[Consumer] Simulating failure for paymentId={} amount={}",
                    event.getPaymentId(), event.getAmount());
            throw new RuntimeException("Simulated processing failure");
        }

        log.info("[Consumer] Processed paymentId={}", event.getPaymentId());

        // simulate poison pill for testing retry logic
//        if (event.getAmount().compareTo(new BigDecimal("9000")) > 0) {
//            log.warn("[Consumer] Simulating failure for paymentId={} amount={}",
//                    event.getPaymentId(), event.getAmount());
//            throw new RuntimeException("Simulated processing failure");
//        }
    }
}
