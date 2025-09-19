package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import lombok.Data;

@Data
public class KafkaMessageBase{
    KafkaMessageMeta meta;
    private String messageCode;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
