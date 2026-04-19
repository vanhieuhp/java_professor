package dev.hieunv.bankos.service;

import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@EnableScheduling
public class OutboxRelayService {
    private final OutboxEventRepository outboxEventRepository;

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

    private void publishToKafka(OutboxEvent event) {
        // Simulate Kafka publish — replace with real kafkaTemplate.send() in production
        // Uncomment to simulate publish failure:
        // if (event.getRetryCount() == 0) throw new RuntimeException("Kafka unavailable");

        log.info("[Kafka] → topic=payment.processed payload={}", event.getPayload());
    }
}
