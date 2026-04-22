package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AuditConsumer {

    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-audit-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();
        log.info("[Audit] Recording payment paymentId={} accountId={} amount={} occurredAt={}",
                event.getPaymentId(), event.getAccountId(),
                event.getAmount(), event.getOccurredAt());
        // TODO: save to audit_log table
    }
}
