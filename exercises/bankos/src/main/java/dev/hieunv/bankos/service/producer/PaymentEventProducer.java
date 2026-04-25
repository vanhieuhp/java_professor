package dev.hieunv.bankos.service.producer;

import dev.hieunv.bankos.avro.PaymentProcessedEvent;
import dev.hieunv.bankos.model.Payment;
import lombok.extern.slf4j.Slf4j;
import org.apache.avro.specific.SpecificRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZoneOffset;

@Component
@Slf4j
public class PaymentEventProducer {
    private static final String TOPIC = "payment-events";

    private final KafkaTemplate<String, SpecificRecord> avroKafkaTemplate;

    public PaymentEventProducer(
            @Qualifier("avroKafkaTemplate")
            KafkaTemplate<String, SpecificRecord> avroKafkaTemplate) {
        this.avroKafkaTemplate = avroKafkaTemplate;
    }


    public void sendPaymentEvent(dev.hieunv.bankos.dto.payment.PaymentProcessedEvent dto) {
        // Use the Avro generated class (dev.hieunv.bankos.avro.PaymentProcessedEvent)
        PaymentProcessedEvent avroEvent = PaymentProcessedEvent.newBuilder()
                .setPaymentId(dto.getPaymentId().toString())
                .setAccountId(dto.getAccountId())
                .setAmount(dto.getAmount())
                .setBalanceAfter(dto.getBalanceAfter() != null
                        ? dto.getBalanceAfter()
                        : BigDecimal.ZERO)
                .setOccurredAt(dto.getOccurredAt().toInstant(ZoneOffset.UTC))
                .setStatus(dto.getStatus())
                .build();

        String key = dto.getAccountId().toString();

        try {
            SendResult<String, SpecificRecord> result =
                    avroKafkaTemplate.send(TOPIC, key, avroEvent).get();
            log.info("[Avro] Sent paymentId={} accountId={} → partition={} offset={}",
                    dto.getPaymentId(),
                    dto.getAccountId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to send Avro payment event paymentId=" + dto.getPaymentId(), e);
        }
    }
}
