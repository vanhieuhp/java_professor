package dev.hieunv.outboxpattern.dto.kafka;

import com.google.gson.Gson;
import dev.hieunv.outboxpattern.constant.EventType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class KafkaMessageMeta {
    protected String messageId;
    private String originalMessageId;
    private EventType type;
    private String serviceId;
    private long timestamp;
    private boolean autoRetry;

    public KafkaMessageMeta() {
        this.autoRetry = false;
    }

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
