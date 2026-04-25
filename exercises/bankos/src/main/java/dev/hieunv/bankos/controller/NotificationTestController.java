package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.notification.UserRegisteredEvent;
import dev.hieunv.bankos.dto.notification.WalletLockedEvent;
import dev.hieunv.bankos.dto.payment.PaymentCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/notifications/test")
@Slf4j
public class NotificationTestController {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationTestController(
            @Qualifier("paymentKafkaTemplate")
            KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @PostMapping("/user-registered")
    public ResponseEntity<String> testUserRegistered(
            @RequestBody UserRegisteredEvent event) {
        event.setOccurredAt(LocalDateTime.now());
        kafkaTemplate.send("user.registered", event.getUserId(), event);
        log.info("[Test] Published user.registered userId={}", event.getUserId());
        return ResponseEntity.ok("Published user.registered for userId=" + event.getUserId());
    }

    @PostMapping("/payment-completed")
    public ResponseEntity<String> testPaymentCompleted(
            @RequestBody PaymentCompletedEvent event) {
        event.setOccurredAt(LocalDateTime.now());
        kafkaTemplate.send("payment.completed", event.getSagaId(), event);
        log.info("[Test] Published payment.completed paymentId={}", event.getPaymentId());
        return ResponseEntity.ok("Published payment.completed for paymentId=" + event.getPaymentId());
    }

    @PostMapping("/wallet-locked")
    public ResponseEntity<String> testWalletLocked(
            @RequestBody WalletLockedEvent event) {
        event.setOccurredAt(LocalDateTime.now());
        kafkaTemplate.send("wallet.locked", event.getAccountId().toString(), event);
        log.info("[Test] Published wallet.locked accountId={}", event.getAccountId());
        return ResponseEntity.ok("Published wallet.locked for accountId=" + event.getAccountId());
    }
}
