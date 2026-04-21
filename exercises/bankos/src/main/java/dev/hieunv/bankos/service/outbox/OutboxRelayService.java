package dev.hieunv.bankos.service.outbox;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class OutboxRelayService {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private static final String PAYMENT_TOPIC = "payment-events";

    // Polls every 2 seconds — in production this would be Debezium CDC
    // or a dedicated relay process reading the outbox table
    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void relay() {
        List<OutboxEvent> pending = outboxEventRepository.findPendingEvents();
        if (pending.isEmpty()) return;

        log.info("[Relay] Found {} pending events", pending.size());

        for (OutboxEvent event : pending) {
            try {
                publishToKafka(event);

                // Mark as published — only after successful publish
                event.setStatus("PUBLISHED");
                event.setPublishedAt(LocalDateTime.now());
                outboxEventRepository.save(event);

                log.info("[Relay] Published eventId={} type={} aggregateId={}",
                        event.getId(), event.getEventType(), event.getAggregateId());

            } catch (Exception e) {
                // Failed to publish — increment retry count
                // Relay will try again on next poll cycle
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 3) {
                    event.setStatus("FAILED");
                    log.error("[Relay] Event ID={} failed after 3 retries — marked FAILED",
                            event.getId());
                } else {
                    log.warn("[Relay] Event ID={} publish failed retry={} — will retry",
                            event.getId(), event.getRetryCount());
                }
                outboxEventRepository.save(event);
            }
        }
    }

    private void publishToKafka(OutboxEvent event) throws ExecutionException, InterruptedException {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);
        if ("PAYMENT_PROCESSED".equals(event.getEventType())) {
            PaymentProcessedEvent kafkaEvent = PaymentProcessedEvent.builder()
                    .paymentId(event.getAggregateId())
                    .accountId(Long.valueOf(payload.get("accountId").toString()))
                    .amount(new BigDecimal(payload.get("amount").toString()))
                    .status(payload.get("status").toString())
                    .processedAt(LocalDateTime.now())
                    .build();

            String key = kafkaEvent.getAccountId().toString();
            kafkaTemplate.send(PAYMENT_TOPIC, key, kafkaEvent).get();
        }

        log.info("[Kafka] → topic=payment.processed payload={}", event.getPayload());
    }
}
