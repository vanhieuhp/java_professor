package dev.hieunv.bankos.service.consumer;


import dev.hieunv.bankos.dto.notification.WalletLockedEvent;
import dev.hieunv.bankos.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class WalletLockedNotificationConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL = Duration.ofHours(24);

    @KafkaListener(
            topics = "wallet.locked",
            groupId = "bankos-notification-wallet-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        WalletLockedEvent event = (WalletLockedEvent) record.value();

        if (event.getReason().equals("SIMULATE_FAILURE")) {
            throw new RuntimeException("Simulated notification failure");
        }
        String emailKey = "notification:wallet:locked:" + event.getAccountId() + ":email";
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(emailKey, "SENT", TTL);

        if (Boolean.TRUE.equals(isNew)) {
            notificationService.sendEmail(
                    "account-" + event.getAccountId() + "@bankos.com",
                    "Security Alert — Wallet Locked",
                    "Your wallet has been locked. Reason: " + event.getReason()
            );
            log.info("[Notification] Wallet lock alert sent accountId={}", event.getAccountId());
        } else {
            log.info("[Notification] Duplicate — skipping accountId={}", event.getAccountId());
        }
    }
}
