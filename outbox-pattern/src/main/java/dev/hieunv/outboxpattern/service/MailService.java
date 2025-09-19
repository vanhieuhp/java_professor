package dev.hieunv.outboxpattern.service;

import dev.hieunv.outboxpattern.dto.MailMessageRequest;
import jakarta.mail.MessagingException;
import jakarta.validation.Valid;
import org.springframework.mail.MailException;

public interface MailService {

    void sendMail(@Valid MailMessageRequest request)  throws MessagingException, MailException;

    void processMail(MailMessageRequest payload);

    void retryMailEvent(MailMessageRequest payload);
}
