package dev.hieunv.bankos;

import dev.hieunv.bankos.model.ExchangeRate;
import dev.hieunv.bankos.repository.ExchangeRateRepository;
import dev.hieunv.bankos.service.ExchangeRateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
@Transactional(propagation = Propagation.NEVER)
public class ExchangeRateCacheTest {

    @Autowired
    private ExchangeRateService exchangeRateService;
    @Autowired
    private ExchangeRateRepository repository;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setup() {
        repository.deleteAll();
        redisTemplate.delete("exchange_rate:USD_VND");
        repository.save(new ExchangeRate("USD_VND",
                new BigDecimal("25000.00")));
    }

    @Test
    void demonstrateTwoLevelCacheHitOrder() {
        System.out.println("=== TWO-LEVEL CACHE HIT ORDER ===\n");

        // First call — both caches empty, loads from DB
        System.out.println("--- Call 1: cold cache ---");
        BigDecimal rate1 = exchangeRateService.getRate("USD_VND");
        assertThat(rate1).isEqualByComparingTo("25000.00");

        // Second call — Redis hit (local cache may have expired)
        System.out.println("\n--- Call 2: Redis hit ---");
        BigDecimal rate2 = exchangeRateService.getRate("USD_VND");
        assertThat(rate2).isEqualByComparingTo("25000.00");

        // Third call — local cache hit
        System.out.println("\n--- Call 3: local cache hit ---");
        BigDecimal rate3 = exchangeRateService.getRate("USD_VND");
        assertThat(rate3).isEqualByComparingTo("25000.00");

        // All three return same value
        assertThat(rate1).isEqualByComparingTo(rate2);
        assertThat(rate2).isEqualByComparingTo(rate3);

        exchangeRateService.printCacheStats();
    }

    @Test
    void demonstrateCacheInvalidation_multiInstance()
            throws InterruptedException {

        System.out.println("=== CACHE INVALIDATION — MULTI INSTANCE ===\n");

        // Instance A warms its local cache
        System.out.println("--- Instance A warms cache ---");
        BigDecimal oldRate = exchangeRateService.getRate("USD_VND");
        assertThat(oldRate).isEqualByComparingTo("25000.00");

        // Rate changes — update DB + invalidate Redis
        System.out.println("\n--- Rate updated to 25500 ---");
        exchangeRateService.updateRate("USD_VND", new BigDecimal("25500.00"));

        // Instance A local cache still has old value for up to 5s
        // This is the eventual consistency window
        System.out.println("\n--- Instance B reads immediately ---");
        BigDecimal rateAfterUpdate = exchangeRateService.getRate("USD_VND");

        // Redis was invalidated — loads fresh from DB
        assertThat(rateAfterUpdate).isEqualByComparingTo("25500.00");
        System.out.println("Instance B sees new rate: $" + rateAfterUpdate
                + " ✅");

        // Wait for local cache TTL to expire (5 seconds)
        System.out.println("\n--- Waiting 6s for local cache to expire ---");
        Thread.sleep(6000);

        // Now Instance A also sees new rate
        BigDecimal rateAfterTtl =
                exchangeRateService.getRate("USD_VND");
        assertThat(rateAfterTtl).isEqualByComparingTo("25500.00");
        System.out.println("Instance A now sees new rate: $"
                + rateAfterTtl + " ✅");
    }

    @Test
    void demonstrateRedisDown_gracefulDegradation()
            throws InterruptedException {

        System.out.println("=== REDIS DOWN — GRACEFUL DEGRADATION ===\n");

        // Warm local cache first
        exchangeRateService.getRate("USD_VND");

        // Simulate Redis being unavailable by deleting the key
        // In production this would be a connection failure
        redisTemplate.delete("exchange_rate:USD_VND");

        // Local cache still serves requests
        System.out.println("--- Redis key gone, local cache serves ---");
        BigDecimal rate = exchangeRateService.getRate("USD_VND");

        // Gets served from local cache or falls back to DB
        assertThat(rate).isEqualByComparingTo("25000.00");
        System.out.println("Still got rate: $" + rate
                + " (local cache or DB fallback) ✅");
    }

    @Test
    void demonstrateConcurrentReads_noStarvation()
            throws InterruptedException {

        System.out.println("=== CONCURRENT READS — NO STARVATION ===\n");
        System.out.println("10 instances reading simultaneously...\n");

        List<BigDecimal> results = Collections.synchronizedList(new ArrayList<>());
        List<Long> readTimes = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(10);

        // Simulate 10 instances reading simultaneously
        for (int i = 0; i < 10; i++) {
            new Thread(() -> {
                try {
                    latch.await();
                    long start = System.currentTimeMillis();
                    BigDecimal rate =
                            exchangeRateService.getRate("USD_VND");
                    readTimes.add(System.currentTimeMillis() - start);
                    results.add(rate);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        latch.countDown(); // release all threads simultaneously
        done.await(10, TimeUnit.SECONDS);

        System.out.println("Read times: " + readTimes);

        // ── Assertions ────────────────────────────────────────

        // All 10 instances got a result
        assertThat(results).hasSize(10);

        // All got the same rate — consistency
        assertThat(results).allSatisfy(r ->
                assertThat(r).isEqualByComparingTo("25000.00"));

        // All reads completed quickly — no starvation
        assertThat(readTimes).allSatisfy(t ->
                assertThat(t).isLessThan(500L));

        System.out.println("\n✅ All 10 instances read consistently"
                + " with no starvation!");

        exchangeRateService.printCacheStats();
    }
}
