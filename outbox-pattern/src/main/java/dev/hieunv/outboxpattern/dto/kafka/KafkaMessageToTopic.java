package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import lombok.Data;

@Data
public class KafkaMessageToTopic {
    private String title;
    private String content;
    private String dataBody;
    private String token;
    private boolean isPushNotification;
    private String topic;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
