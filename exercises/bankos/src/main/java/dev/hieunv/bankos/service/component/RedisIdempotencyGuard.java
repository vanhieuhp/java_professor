package dev.hieunv.bankos.service.component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@RequiredArgsConstructor
@Slf4j
public class RedisIdempotencyGuard {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String LOCK_PREFIX = "lock:payment:";
    private static final Duration LOCK_TTL  = Duration.ofSeconds(30);

    // Returns true if lock acquired, false if already locked
    public boolean tryAcquire(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey;

        // SET key value NX PX 30000
        // NX = only set if not exists (atomic!)
        // PX = TTL in milliseconds
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(lockKey, "LOCKED", LOCK_TTL);

        boolean result = Boolean.TRUE.equals(acquired);
        log.info("[RedisLock] key={} acquired={}", lockKey, result);
        return result;
    }

    public void release(String idempotencyKey) {
        String lockKey = LOCK_PREFIX + idempotencyKey;
        redisTemplate.delete(lockKey);
        log.info("[RedisLock] key={} released", lockKey);
    }

    public boolean isLocked(String idempotencyKey) {
        return Boolean.TRUE.equals(
                redisTemplate.hasKey(LOCK_PREFIX + idempotencyKey));
    }
}
