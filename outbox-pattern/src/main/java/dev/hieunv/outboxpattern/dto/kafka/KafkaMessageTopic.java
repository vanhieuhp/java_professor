package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import lombok.Data;

import java.util.List;

@Data
public class KafkaMessageTopic {
    private List<String> accountIds;
    private boolean isPushNotification;
    private String topic;
    private String title;
    private String content;
    private String dataBody;
    private int systemNotifyType;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
