package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PriceCacheServiceImpl implements PriceCacheService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);
    private static final String KEY_PREFIX   = "price:";
    private static final String KEY_HITS     = "stats:cache:hits";
    private static final String KEY_MISSES   = "stats:cache:misses";

    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceAggregatorService aggregator;

    @Override
    @SuppressWarnings("unchecked")
    public List<PriceResult> getPrices(String product) {
        String key = KEY_PREFIX + product;

        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            redisTemplate.opsForValue().increment(KEY_HITS);
            log.debug("Cache hit for '{}'", product);
            return (List<PriceResult>) cached;
        }

        // Cache miss — fetch from all suppliers in parallel
        redisTemplate.opsForValue().increment(KEY_MISSES);
        log.debug("Cache miss for '{}' — fetching from suppliers", product);
        List<PriceResult> fresh = aggregator.fetchAllPrices(product);

        // Store in Redis — auto-expires after TTL, no manual cleanup needed
        redisTemplate.opsForValue().set(key, fresh, CACHE_TTL);
        return fresh;
    }

    @Override
    public List<PriceResult> getPricesV2(String product) {
        // V2 (the race-condition version) is now identical — Redis GET/SET is simpler
        return getPrices(product);
    }

    @Override
    public Map<String, Long> getStats() {
        Long hits   = toLong(redisTemplate.opsForValue().get(KEY_HITS));
        Long misses = toLong(redisTemplate.opsForValue().get(KEY_MISSES));
        return Map.of(
            "hits",   hits,
            "misses", misses
        );
    }

    private long toLong(Object val) {
        if (val == null) return 0L;
        return Long.parseLong(val.toString());
    }
}
