package dev.hieunv.riskassessment.event;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerChangedEventConsumer {

    private final CustomerChangeProcessor processor;
    private final ObjectMapper objectMapper;

    private final LongAdder received = new LongAdder();
    private final LongAdder poisoned = new LongAdder();

    @KafkaListener(topics = "${pcrt.topic.customer-changed}", groupId = "${spring.kafka.consumer.group-id}")
    public void onCustomerChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        received.increment();

        CustomerChangedEvent event;
        try {
            event = objectMapper.readValue(record.value(), CustomerChangedEvent.class);
        } catch (JacksonException e) {
            skipPoison(record, ack, e.getMessage());
            return;
        }

        try {
            processor.process(event, record.value(), CoreEventSource.KAFKA);
        } catch (PoisonEventException e) {
            skipPoison(record, ack, e.getMessage());
            return;
        }
        ack.acknowledge();
    }

    private void skipPoison(ConsumerRecord<String, String> record, Acknowledgment ack, String reason) {
        poisoned.increment();
        log.error("POISON EVENT skipped — topic={} partition={} offset={} key={} reason={} payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), reason, record.value());
        ack.acknowledge();
    }

    public ConsumerStats stats() {
        return ConsumerStats.builder()
                .received(received.sum())
                .poisoned(poisoned.sum())
                .build();
    }

    @Builder
    @Getter
    public static class ConsumerStats {
        private final long received;
        private final long poisoned;
    }
}
