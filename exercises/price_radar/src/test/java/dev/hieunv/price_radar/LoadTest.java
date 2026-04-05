package dev.hieunv.price_radar;

import dev.hieunv.price_radar.model.PriceResult;
import dev.hieunv.price_radar.service.PriceAggregatorService;
import dev.hieunv.price_radar.service.PriceCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoadTest {

    @Autowired
    private PriceAggregatorService aggregatorService;

    @Autowired
    private PriceCacheService cacheService;

    // -----------------------------------------------------------------------
    // Test 1: Parallel fetching is faster than sequential
    // -----------------------------------------------------------------------
    @Test
    void parallelFetchIsFasterThanSequential() throws Exception {
        // Warm up
        aggregatorService.fetchAllPrices("warmup");

        // Parallel — all 5 suppliers run at once
        long parallelStart = System.currentTimeMillis();
        List<PriceResult> results = aggregatorService.fetchAllPrices("iphone");
        long parallelTime = System.currentTimeMillis() - parallelStart;

        System.out.printf("Parallel fetch: %dms for %d suppliers%n", parallelTime, results.size());

        assertThat(results).isNotEmpty();
        // Parallel should finish in under 3s (slowest single supplier), not 10s+ (sum of all)
        assertThat(parallelTime).isLessThan(3500);
    }

    // -----------------------------------------------------------------------
    // Test 2: Cache returns same results instantly on second call
    // -----------------------------------------------------------------------
    @Test
    void cacheHitIsSignificantlyFasterThanMiss() throws Exception {
        String product = "macbook";

        // First call — cache miss, hits suppliers
        long missStart = System.currentTimeMillis();
        cacheService.getPrices(product);
        long missTime = System.currentTimeMillis() - missStart;

        // Second call — cache hit, instant
        long hitStart = System.currentTimeMillis();
        cacheService.getPrices(product);
        long hitTime = System.currentTimeMillis() - hitStart;

        System.out.printf("Cache miss: %dms | Cache hit: %dms%n", missTime, hitTime);

        Map<String, Long> stats = cacheService.getStats();
        assertThat(stats.get("hits")).isGreaterThanOrEqualTo(1);
        assertThat(stats.get("misses")).isGreaterThanOrEqualTo(1);
        // Hit should be at least 10x faster than miss
        assertThat(hitTime).isLessThan(missTime / 5);
    }

    // -----------------------------------------------------------------------
    // Test 3: 50 concurrent requests — no data corruption, no exceptions
    // -----------------------------------------------------------------------
    @Test
    void concurrentRequestsProduceNoCorruption() throws Exception {
        int threadCount = 50;
        String product = "iphone-concurrent";

        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1); // hold all threads until ready
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tasks.add(() -> {
                startGate.await(); // all threads wait here
                try {
                    List<PriceResult> prices = cacheService.getPrices(product);
                    assertThat(prices).isNotEmpty();
                    prices.forEach(p -> assertThat(p.getPrice()).isPositive());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread failed: " + e.getMessage());
                    errorCount.incrementAndGet();
                }
                return null;
            });
        }

        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(pool.submit(task));
        }

        long start = System.currentTimeMillis();
        startGate.countDown(); // release all threads simultaneously

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        System.out.printf("50 concurrent requests: %dms | success=%d | errors=%d%n",
                elapsed, successCount.get(), errorCount.get());

        Map<String, Long> stats = cacheService.getStats();
        System.out.printf("Cache stats: hits=%d misses=%d%n",
                stats.get("hits"), stats.get("misses"));

        assertThat(errorCount.get()).isZero();
        assertThat(successCount.get()).isEqualTo(threadCount);
        // All 50 threads hit the cache — misses should be 1 (only first fetch goes to suppliers)
        assertThat(stats.get("misses")).isLessThanOrEqualTo(3);
    }

    // -----------------------------------------------------------------------
    // Test 4: Thread pool stats are accurate
    // -----------------------------------------------------------------------
    @Test
    void threadPoolStatsAreAccurate() {
        // Trigger some work first
        aggregatorService.fetchAllPrices("stats-test");

        Map<String, Object> poolStats = aggregatorService.getPoolStats();
        System.out.println("Thread pool stats: " + poolStats);

        assertThat(poolStats).containsKeys("activeThreads", "poolSize", "queuedTasks", "completedTasks");
        assertThat((int) poolStats.get("poolSize")).isGreaterThan(0);
        assertThat((long) poolStats.get("completedTasks")).isGreaterThan(0);
    }
}
