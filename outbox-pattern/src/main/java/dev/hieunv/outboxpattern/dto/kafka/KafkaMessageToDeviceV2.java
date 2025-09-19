package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import lombok.Data;

@Data
public class KafkaMessageToDeviceV2 {
    private String phoneNumber;
    private String data;
    private String apiKey;
    private String clientId;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
