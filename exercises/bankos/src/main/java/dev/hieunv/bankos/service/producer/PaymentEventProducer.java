package dev.hieunv.bankos.service.producer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventProducer {
    private static final String TOPIC = "payment-events";
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public void sendPaymentEvent(PaymentProcessedEvent event) {
        String key = event.getAccountId().toString();
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(TOPIC, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("[Kafka] Failed to send paymentId={} accountId={} error={}",
                        event.getPaymentId(), event.getAccountId(), ex.getMessage());
            } else {
                log.info("[Kafka] Sent paymentId={} accountId={} → partition={} offset={}",
                        event.getPaymentId(),
                        event.getAccountId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }
}
