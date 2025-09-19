package dev.hieunv.outboxpattern.entity;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Builder
@Data
@Document(collection = "OUTBOX_MAIL_EVENT")
public class OutboxMailEntity {

    private String id;
    private String mailId;
    private String subject;
    private String data;
    private int retryCount;
    private int status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Override
    public String toString() {
        return new Gson().toString();
    }
}
