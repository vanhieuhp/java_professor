package dev.hieunv.price_radar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final int MAX_REQUESTS = 10;
    private static final DateTimeFormatter MINUTE_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final RedisTemplate<String, Object> redisTemplate;

    // Track which products have been seen (for getCounters reporting)
    private final Set<String> trackedProducts = ConcurrentHashMap.newKeySet();

    @Override
    public boolean isAllowed(String product) {
        String minute = LocalDateTime.now().format(MINUTE_FMT);
        String key = "ratelimit:" + product + ":" + minute;

        trackedProducts.add(product);

        // INCR is atomic in Redis — safe across all instances simultaneously
        Long count = redisTemplate.opsForValue().increment(key);

        // Set TTL only on the first request (key is brand new)
        if (count != null && count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }

        return count != null && count <= MAX_REQUESTS;
    }

    @Override
    public Map<String, Integer> getCounters() {
        String minute = LocalDateTime.now().format(MINUTE_FMT);
        Map<String, Integer> result = new HashMap<>();
        for (String product : trackedProducts) {
            String key = "ratelimit:" + product + ":" + minute;
            Object val = redisTemplate.opsForValue().get(key);
            result.put(product, val != null ? ((Number) val).intValue() : 0);
        }
        return result;
    }
}
