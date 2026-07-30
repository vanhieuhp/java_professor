package dev.hieunv.ssenotification.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface SseEmitterRegistry {
    /**
     * @return true nếu subscribe thành công, false nếu bị từ chối do vượt giới hạn kết nối
     */
    boolean subscribe(String name, SseEmitter emitter, Long lastEventId);
    void push(String name, String eventName, Object data);
    void forceDisconnect(String name);
}
