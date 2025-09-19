package dev.hieunv.outboxpattern.dto;

import com.google.gson.Gson;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MailMessageRequest {

    @NotBlank
    @Email
    private String sender;

    private String senderName;

    @NotEmpty
    private List<@Email String> recipients;

    private List<@Email String> cc;
    private List<@Email String> bcc;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    private boolean html; // true if body is HTML, false if plain text

    private String outboxEventId;
    private String mailId;

    @Override
    public String toString() {
        return new Gson().toJson(this);
    }
}
