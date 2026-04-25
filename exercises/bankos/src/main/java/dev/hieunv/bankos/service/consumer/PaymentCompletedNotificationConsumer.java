package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentCompletedEvent;
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
public class PaymentCompletedNotificationConsumer {
    private final NotificationService notificationService;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final Duration TTL = Duration.ofHours(24);

    @KafkaListener(
            topics = "payment.completed",
            groupId = "bankos-notification-payment-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentCompletedEvent event = (PaymentCompletedEvent) record.value();

        String emailKey = "notification:payment:completed:" + event.getPaymentId() + ":email";
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(emailKey, "SENT", TTL);

        if (Boolean.TRUE.equals(isNew)) {
            notificationService.sendEmail(
                    "user-" + event.getOrderId() + "@bankos.com",
                    "Payment Confirmed",
                    "Your payment of " + event.getAmount() + " has been processed."
            );
            log.info("[Notification] Payment email sent paymentId={}", event.getPaymentId());
        } else {
            log.info("[Notification] Duplicate — skipping paymentId={}", event.getPaymentId());
        }
    }
}
