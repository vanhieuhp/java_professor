package dev.hieunv.price_radar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertNotificationListener {

    private final NotificationService notificationService;

    /**
     * Called by Redis Pub/Sub on EVERY instance that receives the message.
     * Only the instance that holds the user's SSE emitter will actually push.
     */
    @SuppressWarnings("unchecked")
    public void onAlertTriggered(Object rawMessage) {
        try {
            Map<String, Object> message = (Map<String, Object>) rawMessage;
            String userId = (String) message.get("userId");
            log.info("Received alert notification from Redis for userId={}", userId);
            // pushToUser is a no-op if this instance doesn't hold the SSE connection
            notificationService.pushToUser(userId, message);
        } catch (Exception e) {
            log.warn("Failed to process alert notification: {}", e.getMessage());
        }
    }
}
