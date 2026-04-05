package dev.hieunv.price_radar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {
    // key = userId, value = their open SSE connection
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(5 * 60 * 1000L); // 5 minutes timeout
        // clean up when connection closes (browser tab closed, timeout, error)
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError(t -> emitters.remove(userId));

        emitters.put(userId, emitter);
        log.info("User {} subscribed to notifications", userId);
        return emitter;

    }

    @Override
    public void sendNotification(String userId, Object payload) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter == null) {
            return; // user not connected - skip silently
        }

        try {
            emitter.send(SseEmitter.event()
                    .name("price-alert")
                    .data(payload));
        } catch (IOException e) {
            emitters.remove(userId);
        }
    }

    @Override
    public void pushToUser(String userId, Object payload) {
        // Same logic as sendNotification — silently skipped if no emitter on this instance
        sendNotification(userId, payload);
    }

    @Override
    public int activeConnections() {
        return emitters.size();
    }
}
