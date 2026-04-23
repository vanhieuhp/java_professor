package dev.hieunv.bankos.service.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.hieunv.bankos.dto.payment.PaymentStatEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PaymentStatsConsumer {
    private static final long FRAUD_THRESHOLD = 3L; // low value for easy testing

    @KafkaListener(
            topics = "payment-stats",
            groupId = "bankos-fraud-detection-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        try {
            ObjectMapper mapper = new ObjectMapper()
                    .registerModule(new JavaTimeModule());
            PaymentStatEvent event = mapper.convertValue(record.value(), PaymentStatEvent.class);

            log.info("[STATS] account={} count={} window={} → {}",
                    event.getAccountId(),
                    event.getPaymentCount(),
                    event.getWindowStart(),
                    event.getWindowEnd());

            if (event.getPaymentCount() >= FRAUD_THRESHOLD) {
                log.warn("[FRAUD ALERT] account={} made {} payments in 5-minute window!",
                        event.getAccountId(), event.getPaymentCount());
            }

        } catch (Exception e) {
            log.error("[STATS] Failed to process stat event: {}", e.getMessage());
        }
    }
}
