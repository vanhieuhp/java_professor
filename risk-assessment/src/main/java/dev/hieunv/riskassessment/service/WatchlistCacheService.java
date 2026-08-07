package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.WatchlistCachePayload;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistCacheService {

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    @Value("${pcrt.watchlist-cache.redis-key:pcrt:watchlist:snapshot}")
    private String key;

    @Value("${pcrt.watchlist-cache.redis-ttl-hours:24}")
    private long ttlHours;

    public Optional<WatchlistCachePayload> find(Instant expectedMark) {
        String json;
        try {
            json = redis.opsForValue().get(key);
        } catch (RuntimeException e) {
            log.warn("Can not read cache from Redis ({}): {}", key, e.toString());
            return Optional.empty();
        }
        if (json == null) {
            return Optional.empty();
        }

        WatchlistCachePayload payload;
        try {
            payload = objectMapper.readValue(json, WatchlistCachePayload.class);
        } catch (RuntimeException e) {
            log.warn("Payload cache can not be deserialized: {}", json);
            evict();
            return Optional.empty();
        }

        if (payload.getSchemaVersion() != WatchlistCachePayload.CURRENT_SCHEMA_VERSION) {
            log.info("Payload cache thuộc schema {} (code đang dùng {}) — bỏ qua",
                    payload.getSchemaVersion(), WatchlistCachePayload.CURRENT_SCHEMA_VERSION);
            return Optional.empty();
        }
        if (!Objects.equals(payload.getLoadedFrom(), expectedMark)) {
            log.info("Payload cache dựng từ mốc {} nhưng DB đang ở mốc {} — bỏ qua",
                    payload.getLoadedFrom(), expectedMark);
            return Optional.empty();
        }
        return Optional.of(payload);
    }

    public void save(WatchlistCachePayload payload) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(payload), Duration.ofHours(ttlHours));
        } catch (RuntimeException e) {
            log.warn("Không ghi được cache danh sách vào Redis ({}): {}", key, e.toString());
        }
    }

    public void evict() {
        try {
            redis.delete(key);
        } catch (RuntimeException e) {
            log.warn("Không xóa được key cache {}: {}", key, e.toString());
        }
    }
}
