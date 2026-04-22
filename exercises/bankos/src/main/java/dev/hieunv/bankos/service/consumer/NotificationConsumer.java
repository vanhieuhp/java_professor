package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class NotificationConsumer {
    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-notification-group"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();
        log.info("[Notification] Sending email for paymentId={} accountId={} amount={}",
                event.getPaymentId(), event.getAccountId(), event.getAmount());
        // TODO: send email
    }
}
