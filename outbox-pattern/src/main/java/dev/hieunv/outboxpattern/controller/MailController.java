package dev.hieunv.outboxpattern.controller;

import dev.hieunv.outboxpattern.dto.MailMessageRequest;
import dev.hieunv.outboxpattern.service.MailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin("*")
@Slf4j
@RestController
@RequestMapping("/v1/mail")
@RequiredArgsConstructor
public class MailController {

    private final MailService mailService;

    @PostMapping("/send")
    public ResponseEntity<?> sendMail(@Valid @RequestBody MailMessageRequest request) {
        log.info("==> [POST /v1/mail/send] Incoming request: {}", request);
        try {
            mailService.sendMail(request);
            return ResponseEntity.ok("Send mail successfully.");
        } catch (Exception e) {
            log.error("Error while sending mail", e);
            return ResponseEntity.status(500).body("Internal server error.");
        }
    }
}
