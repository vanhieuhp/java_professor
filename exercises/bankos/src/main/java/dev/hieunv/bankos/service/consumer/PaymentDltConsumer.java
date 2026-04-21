package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentDltConsumer {

    @KafkaListener(topics = "payment-events-dlt", groupId = "bankos-dlt-group")
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();

        // ── Extract DLT headers added by Spring Kafka ─────────────────
        String exceptionClass   = getHeader(record, "kafka_dlt-exception-fqcn");
        String exceptionMessage = getHeader(record, "kafka_dlt-exception-message");
        String originalTopic    = getHeader(record, "kafka_dlt-original-topic");
        String originalPartition = getHeader(record, "kafka_dlt-original-partition");
        String originalOffset   = getHeader(record, "kafka_dlt-original-offset");

        log.error("[DLT] ══════════════════════════════════════");
        log.error("[DLT] Payment failed after all retries");
        log.error("[DLT] paymentId={}  accountId={}  amount={}",
                event.getPaymentId(), event.getAccountId(), event.getAmount());
        log.error("[DLT] exception={}  message={}",
                exceptionClass, exceptionMessage);
        log.error("[DLT] originalTopic={}  partition={}  offset={}",
                originalTopic, originalPartition, originalOffset);
        log.error("[DLT] ══════════════════════════════════════");

    }

    private String getHeader(ConsumerRecord<?, ?> record, String key) {
        Header header = record.headers().lastHeader(key);
        return header != null ? new String(header.value()) : "unknown";
    }
}
