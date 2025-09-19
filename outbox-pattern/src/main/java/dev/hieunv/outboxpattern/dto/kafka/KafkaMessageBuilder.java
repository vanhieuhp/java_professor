package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import dev.hieunv.outboxpattern.constant.EventType;

import java.util.UUID;

public class KafkaMessageBuilder {
    public static <T> KafkaMessage<T> build(String serviceId, EventType eventType, String messageCode, T payload) {
        KafkaMessage<T> message = new KafkaMessage<>();
        KafkaMessageMeta meta = KafkaMessageMeta.builder()
                .messageId(generateMessageId())
                .serviceId(serviceId)
                .type(eventType)
                .timestamp(System.currentTimeMillis())
                .build();
        message.setMeta(meta);
        message.setMessageCode(messageCode);
        message.setPayload(payload);
        return message;
    }

    public static String generateMessageId() {
        return UUID.randomUUID().toString().replace("_", "");
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
