package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.service.NotificationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    @Override
    public void sendEmail(String to, String subject, String body) {
        // Simulate email sending
        log.info("[Email] TO={} SUBJECT={} BODY={}", to, subject, body);
    }

    @Override
    public void sendSms(String phone, String message) {
        // Simulate SMS sending
        log.info("[SMS] TO={} MESSAGE={}", phone, message);
    }
}
