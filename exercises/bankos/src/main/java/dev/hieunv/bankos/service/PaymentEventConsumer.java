package dev.hieunv.bankos.service;
import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventConsumer {

    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-payment-group"
    )
    public void consume(ConsumerRecord<String, PaymentProcessedEvent> record) {
        PaymentProcessedEvent event = record.value();
        log.info("[Consumer] Received paymentId={} accountId={} amount={} " +
                        "partition={} offset={}",
                event.getPaymentId(),
                event.getAccountId(),
                event.getAmount(),
                record.partition(),
                record.offset());
        // Phase 2 will add: idempotency check, DLT, retry logic
        // For now — just verify the plumbing works
    }
}
