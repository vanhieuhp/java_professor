package dev.hieunv.bankos.service.impl;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import dev.hieunv.bankos.model.ExchangeRate;
import dev.hieunv.bankos.repository.ExchangeRateRepository;
import dev.hieunv.bankos.service.ExchangeRateService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExchangeRateServiceImpl implements ExchangeRateService {

    private static final String REDIS_KEY_PREFIX = "exchange_rate:";

    @Autowired
    private ExchangeRateRepository repository;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private CacheManager cacheManager;
    @Value("${bankos.cache.exchange-rate.redis-ttl-seconds:30}")
    private long redisTtlSeconds;

    @Override
    public BigDecimal getRate(String currencyPair) {

        // Level 1 — local Caffeine cache (~0ms)
        Cache localCache = cacheManager.getCache("exchangeRates");
        Cache.ValueWrapper localValue = localCache.get(currencyPair);

        if (localValue != null) {
            System.out.println("[Cache] L1 HIT  " + currencyPair
                    + " → " + localValue.get());
            return (BigDecimal) localValue.get();
        }

        // Level 2 — Redis shared cache (~1ms)
        String redisKey = REDIS_KEY_PREFIX + currencyPair;
        Object redisValue = redisTemplate.opsForValue().get(redisKey);

        if (redisValue != null) {
            BigDecimal rate = new BigDecimal(redisValue.toString());
            System.out.println("[Cache] L2 HIT  " + currencyPair
                    + " → " + rate);

            // Populate local cache from Redis
            localCache.put(currencyPair, rate);
            return rate;
        }

        // Level 3 — PostgreSQL source of truth (~10ms)
        System.out.println("[Cache] MISS    " + currencyPair + " → loading from DB");

        BigDecimal rate = repository
                .findByCurrencyPair(currencyPair)
                .map(ExchangeRate::getRate)
                .orElseThrow(() -> new EntityNotFoundException(
                        "No rate found for: " + currencyPair));

        // Populate both caches
        redisTemplate.opsForValue().set(
                redisKey, rate.toString(),
                Duration.ofSeconds(redisTtlSeconds));
        localCache.put(currencyPair, rate);

        System.out.println("[Cache] LOADED  " + currencyPair
                + " → " + rate + " (cached in Redis + local)");
        return rate;
    }

    @Transactional
    @Override
    public void updateRate(String currencyPair, BigDecimal newRate) {
        System.out.println("\n[Cache] Updating " + currencyPair
                + " → " + newRate);

        // 1. Persist to PostgreSQL
        ExchangeRate rate = repository
                .findByCurrencyPair(currencyPair)
                .orElse(new ExchangeRate(currencyPair, newRate));
        rate.setRate(newRate);
        repository.save(rate);

        // 2. Invalidate Redis — all instances will reload from DB
        String redisKey = REDIS_KEY_PREFIX + currencyPair;
        redisTemplate.delete(redisKey);
        System.out.println("[Cache] Redis invalidated for " + currencyPair);

        // 3. Invalidate local cache on THIS instance
        Cache localCache = cacheManager.getCache("exchangeRates");
        localCache.evict(currencyPair);
        System.out.println("[Cache] Local cache invalidated for "
                + currencyPair);

        // NOTE: other instances' local caches will expire naturally
        // via Caffeine TTL (5 seconds) — eventual consistency
    }

    @Override
    public void printCacheStats() {
        CaffeineCache caffeineCache = (CaffeineCache) cacheManager.getCache("exchangeRates");
        CacheStats stats = caffeineCache.getNativeCache().stats();

        System.out.println("\n[Cache Stats]"
                + " hits="     + stats.hitCount()
                + " misses="   + stats.missCount()
                + " hitRate="  + String.format("%.1f%%",
                stats.hitRate() * 100));
    }
}
