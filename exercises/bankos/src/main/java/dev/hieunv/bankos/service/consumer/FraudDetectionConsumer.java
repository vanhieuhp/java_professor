package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.listener.ConsumerSeekAware;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Slf4j
@Component
public class FraudDetectionConsumer implements ConsumerSeekAware {

    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-fraud-group"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();
        log.info("[Fraud] Analysing paymentId={} amount={}",
                event.getPaymentId(), event.getAmount());
        // TODO: send to ML model
    }

    @Override
    public void onPartitionsAssigned(Map<TopicPartition, Long> assignments, ConsumerSeekCallback callback) {

        // Seek to 30 days ago on startup
        long thirtyDaysAgo = Instant.now()
                .minus(30, ChronoUnit.DAYS)
                .toEpochMilli();

        assignments.keySet().forEach(
                partition -> {
                    callback.seekToTimestamp(
                            partition.topic(),
                            partition.partition(),
                            thirtyDaysAgo
                    );
                }
        );

        log.info("[Fraud] Seeking to 30 days ago on startup");

    }
}
