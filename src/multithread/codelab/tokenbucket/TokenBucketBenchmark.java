package multithread.codelab.tokenbucket;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class TokenBucketBenchmark {
    static final int NUM_REQUESTS = 100;
    static final int NUM_THREADS = 100;  // all 100 fire at once
    static final double RATE_TPS = 100.0;
    static final double BURST_CAP = 20.0;
    static final int API_DELAY_MS = 50;   // simulated API call cost

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== Token Bucket Rate Limiter Benchmark ===");
        System.out.printf("Requests: %d  |  Rate: %.0f TPS  |  Burst cap: %.0f  |  API latency: %dms%n%n",
                NUM_REQUESTS, RATE_TPS, BURST_CAP, API_DELAY_MS);
        System.out.println("-- Mode 1: Semaphore (concurrency cap only, no rate enforcement) --");
        runSemaphore();

        System.out.println("\n-- Mode 2: Token bucket, tryAcquire (fail-fast, no waiting) --");
        runTokenBucketFailFast();

        System.out.println("\n-- Mode 3: Token bucket, acquire() (blocking, smooth throughput) --");
        runTokenBucketBlocking();
    }

    static void runSemaphore() throws InterruptedException {
        Semaphore sem = new Semaphore(20, true);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        LatencyStats   stats = new LatencyStats(NUM_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    long t0 = System.currentTimeMillis();
                    if (sem.tryAcquire()) {
                        try {
                            simulateApiCall();
                            success.incrementAndGet();
                        } finally {
                            sem.release();
                        }
                    } else {
                        rejected.incrementAndGet();
                    }
                    stats.record(System.currentTimeMillis() - t0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        long wall = runAndMeasure(start, done);
        printResults(wall, success.get(), rejected.get(), stats);
    }

    // ── Token bucket: fail fast ───────────────────────────────────────────
    static void runTokenBucketFailFast() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(RATE_TPS, BURST_CAP);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        LatencyStats   stats = new LatencyStats(NUM_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    long t0 = System.currentTimeMillis();
                    if (bucket.tryAcquire()) {
                        simulateApiCall();
                        success.incrementAndGet();
                    } else {
                        rejected.incrementAndGet();
                    }
                    stats.record(System.currentTimeMillis() - t0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        long wall = runAndMeasure(start, done);
        printResults(wall, success.get(), rejected.get(), stats);
    }
    static void runTokenBucketBlocking() throws InterruptedException {
        TokenBucket bucket = new TokenBucket(RATE_TPS, BURST_CAP);
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        LatencyStats   stats = new LatencyStats(NUM_REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(NUM_THREADS);

        for (int i = 0; i < NUM_THREADS; i++) {
            new Thread(() -> {
                try {
                    start.await();
                    long t0 = System.currentTimeMillis();
                    bucket.acquire();
                    simulateApiCall();
                    success.incrementAndGet();
                    stats.record(System.currentTimeMillis() - t0);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }

        long wall = runAndMeasure(start, done);
        printResults(wall, success.get(), rejected.get(), stats);
    }

    static void simulateApiCall() {
        try {
            Thread.sleep(API_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static long runAndMeasure(CountDownLatch start, CountDownLatch done) throws InterruptedException {
        long t0 = System.currentTimeMillis();
        start.countDown();
        done.await();
        return System.currentTimeMillis() - t0;
    }

    static void printResults(long wallMs, int success, int rejected, LatencyStats stats) {
        double tps = success * 1000.0 / wallMs;
        System.out.printf("  Total time : %d ms%n", wallMs);
        System.out.printf("  Succeeded  : %d  |  Rejected: %d%n", success, rejected);
        System.out.printf("  Throughput : %.1f TPS%n", tps);
        System.out.printf("  Latency    : mean=%.0fms  p50=%dms  p95=%dms  p99=%dms  max=%dms%n",
                stats.mean(), stats.p50(), stats.p95(), stats.p99(), stats.p100());
    }
}
