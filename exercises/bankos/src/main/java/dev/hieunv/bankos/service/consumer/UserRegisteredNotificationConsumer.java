package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.notification.UserRegisteredEvent;
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
public class UserRegisteredNotificationConsumer {

    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL = Duration.ofHours(24);

    @KafkaListener(
            topics = "user.registered",
            groupId = "bankos-notification-user-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        UserRegisteredEvent event = (UserRegisteredEvent) record.value();

        // Email idempotency
        String emailKey = "notification:user:registered:" + event.getUserId() + ":email";
        Boolean isNewEmail = redisTemplate.opsForValue()
                .setIfAbsent(emailKey, "SENT", TTL);

        if (Boolean.TRUE.equals(isNewEmail)) {
            notificationService.sendEmail(
                    event.getEmail(),
                    "Welcome to BankOS!",
                    "Hi " + event.getFullName() + ", welcome to BankOS!"
            );
            log.info("[Notification] Welcome email sent userId={}", event.getUserId());
        } else {
            log.info("[Notification] Duplicate — skipping email userId={}", event.getUserId());
        }

        // SMS idempotency (separate key)
        String smsKey = "notification:user:registered:" + event.getUserId() + ":sms";
        Boolean isNewSms = redisTemplate.opsForValue()
                .setIfAbsent(smsKey, "SENT", TTL);
        if (Boolean.TRUE.equals(isNewSms)) {
            notificationService.sendSms(
                    event.getUserId(),
                    "Welcome to BankOS, " + event.getFullName() + "!"
            );
            log.info("[Notification] Welcome SMS sent userId={}", event.getUserId());
        } else {
            log.info("[Notification] Duplicate — skipping SMS userId={}", event.getUserId());
        }
    }
}
