package dev.hieunv.ssenotification.service;

import tools.jackson.databind.ObjectMapper;
import dev.hieunv.ssenotification.config.RedisConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class SseEmitterRegistryImpl implements SseEmitterRegistry, MessageListener {

    private static final long HEARTBEAT_INTERVAL_SECONDS = 5L;
    private static final int BUFFER_CAPACITY = 50;
    private static final int MAX_CONNECTIONS_PER_USER = 5;

    private record BufferedEvent(long id, String eventName, Object data) {}

    private record PushMessage(String userId, long id, String eventName, Object data) {}

    // Local-only: SseEmitter là object Java gắn với 1 kết nối TCP thật của chính instance này,
    // không thể chia sẻ/serialize sang instance khác.
    private final Map<String, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();

    // Shared qua Redis: mọi instance phải thấy giống nhau, vì user có thể reconnect vào instance khác.
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(this::sendHeartbeats,
                HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    @PreDestroy
    void stopHeartbeat() {
        heartbeatScheduler.shutdownNow();
    }

    private void sendHeartbeats() {
        for (Map.Entry<String, CopyOnWriteArrayList<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (Exception e) {
                    log.warn("[{}] heartbeat failed, completing emitter with error: {}", entry.getKey(), e.getMessage());
                    emitter.completeWithError(e);
                }
            }
        }
    }

    @Override
    public boolean subscribe(String name, SseEmitter emitter, Long lastEventId) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());

        synchronized (userEmitters) {
            if (userEmitters.size() >= MAX_CONNECTIONS_PER_USER) {
                log.warn("[{}] subscribe rejected: already at limit ({}/{})", name, userEmitters.size(), MAX_CONNECTIONS_PER_USER);
                return false;
            }
            userEmitters.add(emitter);
            log.info("[{}] subscribed, connections now {}/{}", name, userEmitters.size(), MAX_CONNECTIONS_PER_USER);
        }

        Runnable cleanup = () -> {
            userEmitters.remove(emitter);
            log.info("[{}] emitter removed, connections now {}/{}", name, userEmitters.size(), MAX_CONNECTIONS_PER_USER);
            if (userEmitters.isEmpty()) {
                emitters.remove(name, userEmitters);
            }
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        if (lastEventId != null) {
            log.info("[{}] replaying missed events since id={}", name, lastEventId);
            replayMissedEvents(name, emitter, lastEventId);
        }

        return true;
    }

    private void replayMissedEvents(String name, SseEmitter emitter, long lastEventId) {
        List<String> raw = redisTemplate.opsForList().range(bufferKey(name), 0, -1);
        if (raw == null) {
            return;
        }

        for (String json : raw) {
            BufferedEvent event = deserialize(json, BufferedEvent.class);
            if (event.id() <= lastEventId) {
                continue;
            }
            try {
                emitter.send(SseEmitter.event()
                        .id(String.valueOf(event.id()))
                        .name(event.eventName())
                        .data(event.data()));
            } catch (Exception e) {
                log.warn("[{}] replay failed, completing emitter with error: {}", name, e.getMessage());
                emitter.completeWithError(e);
                return;
            }
        }
    }

    @Override
    public void push(String name, String eventName, Object data) {
        long id = redisTemplate.opsForValue().increment(seqKey(name));

        BufferedEvent bufferedEvent = new BufferedEvent(id, eventName, data);
        redisTemplate.opsForList().rightPush(bufferKey(name), serialize(bufferedEvent));
        redisTemplate.opsForList().trim(bufferKey(name), -BUFFER_CAPACITY, -1);

        PushMessage message = new PushMessage(name, id, eventName, data);
        redisTemplate.convertAndSend(RedisConfig.SSE_CHANNEL, serialize(message));
        log.info("[{}] published id={} event={} to channel {}", name, id, eventName, RedisConfig.SSE_CHANNEL);
    }

    // Nhận message từ Redis pub/sub - chạy trên MỌI instance đang subscribe kênh, kể cả instance
    // vừa gọi push() ở trên. Instance nào có emitter local của user đó thì gửi, không có thì bỏ qua.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        PushMessage pushMessage = deserialize(body, PushMessage.class);

        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(pushMessage.userId());
        if (userEmitters == null) {
            log.info("[{}] received id={} but no local emitter on this instance, skipping", pushMessage.userId(), pushMessage.id());
            return;
        }

        log.info("[{}] delivering id={} to {} local emitter(s)", pushMessage.userId(), pushMessage.id(), userEmitters.size());
        SseEmitter.SseEventBuilder event = SseEmitter.event()
                .id(String.valueOf(pushMessage.id()))
                .name(pushMessage.eventName())
                .data(pushMessage.data());
        for (SseEmitter emitter : userEmitters) {
            try {
                emitter.send(event);
            } catch (Exception e) {
                log.warn("[{}] push failed, completing emitter with error: {}", pushMessage.userId(), e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }

    @Override
    public void forceDisconnect(String name) {
        CopyOnWriteArrayList<SseEmitter> userEmitters = emitters.get(name);
        if (userEmitters == null) {
            log.info("[{}] forceDisconnect: no local emitter on this instance", name);
            return;
        }
        for (SseEmitter emitter : userEmitters) {
            emitter.complete();
        }
    }

    private String seqKey(String userId) {
        return "sse:seq:" + userId;
    }

    private String bufferKey(String userId) {
        return "sse:buffer:" + userId;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize SSE payload", e);
        }
    }

    private <T> T deserialize(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize SSE payload", e);
        }
    }
}
