package dev.hieunv.bankos.service.consumer;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentDltConsumer {

    @KafkaListener(topics = "payment-events.DLT", groupId = "bankos-dlt-group")
    public void consumer(ConsumerRecord<String, Object> record) {
        log.warn("[DLT] Dead letter received — key={} partition={} offset={}",
                record.key(),
                record.partition(),
                record.offset());

        log.warn("[DLT] Headers: {}",
                record.headers());
    }
}
