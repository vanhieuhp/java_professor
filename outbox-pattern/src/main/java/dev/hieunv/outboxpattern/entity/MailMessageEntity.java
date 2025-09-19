package dev.hieunv.outboxpattern.entity;

import com.google.gson.Gson;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Builder

@Data
@Document(collection = "MAIL_MESSAGES")
public class MailMessageEntity {

    @Id
    private String id;
    private String sender;
    private String senderName;
    private List<String> recipients;
    private List<String> cc;
    private List<String> bcc;
    private String subject;
    private String body;
    private boolean html; // true if body is HTML, false if plain text
    private LocalDateTime createdAt;
    private LocalDateTime  updatedAt;
    private boolean sent;

    @Override
    public String toString() {
        return new Gson().toString();
    }
}
