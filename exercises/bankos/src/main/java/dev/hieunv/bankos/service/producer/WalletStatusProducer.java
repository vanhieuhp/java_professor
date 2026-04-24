package dev.hieunv.bankos.service.producer;

import dev.hieunv.bankos.dto.wallet.WalletStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class WalletStatusProducer {

    private static final String TOPIC = "wallet-status";

    @Qualifier("backgroundKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public WalletStatusProducer(
            @Qualifier("backgroundKafkaTemplate")
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendWalletStatus(WalletStatusEvent event) {
        String key = event.getAccountId().toString();
        try {
            SendResult<String, Object> result = kafkaTemplate.send(TOPIC, key, event).get();
            log.info("[Kafka] Sent wallet status accountId={} → partition={} offset={}",
                    event.getAccountId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
        } catch (Exception e) {
            throw new RuntimeException("Failed to send wallet status event accountId=" + event.getAccountId(), e);
        }
    }
}
