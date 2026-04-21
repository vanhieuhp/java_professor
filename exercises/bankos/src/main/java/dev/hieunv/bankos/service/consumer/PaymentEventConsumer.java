package dev.hieunv.bankos.service.consumer;
import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@Slf4j
public class PaymentEventConsumer {

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

        // simulate poison pill for testing retry logic
        if (event.getAmount().compareTo(new BigDecimal("9000")) > 0) {
            log.warn("[Consumer] Simulating failure for paymentId={} amount={}",
                    event.getPaymentId(), event.getAmount());
            throw new RuntimeException("Simulated processing failure");
        }
    }
}
