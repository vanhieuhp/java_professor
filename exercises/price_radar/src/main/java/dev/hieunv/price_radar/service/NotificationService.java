package dev.hieunv.price_radar.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface NotificationService {
    SseEmitter subscribe(String userId);

    void sendNotification(String userId, Object payload);

    // Called by AlertNotificationListener on every instance — no-op if user not connected here
    void pushToUser(String userId, Object payload);

    int activeConnections();
}
