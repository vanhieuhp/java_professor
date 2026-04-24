package dev.hieunv.bankos.service.producer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventProducer {
    private static final String TOPIC = "payment-events";

    @Qualifier("paymentKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(
            @Qualifier("paymentKafkaTemplate")
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendPaymentEvent(PaymentProcessedEvent event) {
        String key = event.getAccountId().toString();
        try {
            SendResult<String, Object> result = kafkaTemplate.send(TOPIC, key, event).get();
            log.info("[Kafka] Sent paymentId={} accountId={} → partition={} offset={}",
                    event.getPaymentId(),
                    event.getAccountId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            throw new RuntimeException("Failed to send payment event paymentId=" + event.getPaymentId(), e);
        }
    }
}
