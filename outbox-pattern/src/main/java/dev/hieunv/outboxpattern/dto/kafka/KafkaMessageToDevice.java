package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class KafkaMessageToDevice {
    private String title;
    private String content;
    private String dataBody;
    private String token;
    private boolean isPushNotification;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
