package hieunv.dev.netflixstack.counter.redis;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.random.RandomGenerator;

/**
 * Architecture B - Local LongAdder + periodic flush.
 *
 * Each "server" keeps its own LongAdder in RAM. Requests only call
 * adder.increment() - they never touch Redis, so throughput must be far
 * higher than architecture A (no network round trip on the hot path). A
 * separate scheduled task per server runs every FLUSH_INTERVAL_MS, reads
 * adder.sumThenReset(), then calls Jedis.incrBy(KEY, delta) if delta > 0.
 *
 * The trade-off: the value in Redis can lag behind RAM by up to roughly
 * FLUSH_INTERVAL_MS + one incrBy round trip - that's the price of
 * "eventually consistent". If a flush fails, the delta already pulled out
 * by sumThenReset() is lost for good - unlike A, where every INCR is
 * durable in Redis the instant it happens.
 *
 * The benchmark runs in 2 phases:
 *  1) Throughput: N threads/server hammer increment() only, measure ops/sec.
 *  2) Lag: once throughput has settled and Redis holds the exact count,
 *     fire individual "marker" increment() calls and poll GET(KEY) until
 *     the value rises - measuring the real delay between an actual
 *     increment and the moment Redis reflects it.
 */
public class LocalAdderFlushBenchmark {

    private static final String REDIS_HOST = System.getProperty("redis.host", "localhost");
    private static final int REDIS_PORT = Integer.getInteger("redis.port", 6379);
    private static final String KEY = "video:123";

    private static final int SERVERS = Integer.getInteger("servers", 3);
    private static final int THREADS_PER_SERVER = Integer.getInteger("threadsPerServer", 20);
    private static final int WARMUP_OPS_PER_THREAD = Integer.getInteger("warmupOps", 100_000);
    private static final int OPS_PER_THREAD = Integer.getInteger("opsPerThread", 1_000_000);
    private static final long FLUSH_INTERVAL_MS = Long.getLong("flushIntervalMs", 100);

    private static final int LAG_PROBES = Integer.getInteger("lagProbes", 30);
    private static final long LAG_POLL_INTERVAL_MS = Long.getLong("lagPollIntervalMs", 1);
    private static final long LAG_POLL_TIMEOUT_MS = Long.getLong("lagPollTimeoutMs", 2_000);

    private static final int TOTAL_THREADS = SERVERS * THREADS_PER_SERVER;
    private static final long EXPECTED_TOTAL = (long) TOTAL_THREADS * OPS_PER_THREAD;

    private record Server(LongAdder adder, RedisClient redis) {
    }

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Architecture B - Local LongAdder + periodic flush");
        System.out.printf("servers=%d threadsPerServer=%d opsPerThread=%d totalOps=%,d flushIntervalMs=%d redis=%s:%d%n%n",
                SERVERS, THREADS_PER_SERVER, OPS_PER_THREAD, EXPECTED_TOTAL, FLUSH_INTERVAL_MS, REDIS_HOST, REDIS_PORT);

        Server[] servers = new Server[SERVERS];
        for (int s = 0; s < SERVERS; s++) {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(2);
            RedisClient redis = RedisClient.builder()
                    .hostAndPort(REDIS_HOST, REDIS_PORT)
                    .poolConfig(poolConfig)
                    .build();
            servers[s] = new Server(new LongAdder(), redis);
        }

        CountDownLatch ready = new CountDownLatch(TOTAL_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(TOTAL_THREADS);

        for (int s = 0; s < SERVERS; s++) {
            LongAdder adder = servers[s].adder();
            for (int t = 0; t < THREADS_PER_SERVER; t++) {
                Thread worker = new Thread(
                        () -> runWorker(adder, ready, start, done),
                        "server-%d-thread-%d".formatted(s, t));
                worker.start();
            }
        }

        // Warmup already filled the adders - clear them before starting the
        // flush scheduler, otherwise warmup counts would leak into Redis and
        // break the final comparison.
        ready.await();
        for (Server server : servers) {
            server.adder().reset();
        }
        servers[0].redis().del(KEY);

        ScheduledExecutorService[] flushers = startFlushers(servers);

        long startedAt = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedNanos = System.nanoTime() - startedAt;

        stopFlushersAndDrain(flushers, servers);

        reportThroughput(elapsedNanos);
        checkFinalValue(servers[0].redis());

        System.out.println();
        ScheduledExecutorService[] lagFlushers = startFlushers(servers);
        measureLag(servers);
        stopFlushersAndDrain(lagFlushers, servers);

        for (Server server : servers) {
            server.redis().close();
        }
    }

    private static ScheduledExecutorService[] startFlushers(Server[] servers) {
        ScheduledExecutorService[] flushers = new ScheduledExecutorService[servers.length];
        for (int s = 0; s < servers.length; s++) {
            Server server = servers[s];
            int serverIndex = s;
            ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "flush-server-" + serverIndex));
            flusher.scheduleAtFixedRate(() -> flush(server), FLUSH_INTERVAL_MS, FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
            flushers[s] = flusher;
        }
        return flushers;
    }

    private static void stopFlushersAndDrain(ScheduledExecutorService[] flushers, Server[] servers) {
        for (ScheduledExecutorService flusher : flushers) {
            flusher.shutdown();
            try {
                flusher.awaitTermination(FLUSH_INTERVAL_MS * 2, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        // One final manual flush: make sure whatever accumulated in the
        // adder between the last scheduled flush and the scheduler stopping
        // isn't lost.
        for (Server server : servers) {
            flush(server);
        }
    }

    private static void flush(Server server) {
        long delta = server.adder().sumThenReset();
        if (delta > 0) {
            try {
                server.redis().incrBy(KEY, delta);
            } catch (RuntimeException e) {
                System.err.println("Flush failed, lost " + delta + " count: " + e);
            }
        }
    }

    private static void runWorker(LongAdder adder, CountDownLatch ready, CountDownLatch start, CountDownLatch done) {
        try {
            for (int i = 0; i < WARMUP_OPS_PER_THREAD; i++) {
                adder.increment();
            }

            ready.countDown();
            await(start);

            for (int i = 0; i < OPS_PER_THREAD; i++) {
                adder.increment();
            }
        } finally {
            done.countDown();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void reportThroughput(long elapsedNanos) {
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = EXPECTED_TOTAL / elapsedSeconds;
        System.out.printf("Elapsed:      %,.3f s%n", elapsedSeconds);
        System.out.printf("Throughput:   %,.0f ops/sec (request path, no Redis involved)%n", throughput);
    }

    private static void checkFinalValue(RedisClient check) {
        long finalValue = readCounter(check);
        System.out.printf("Final counter value (after draining all flushes): %,d (expected %,d) -> %s%n",
                finalValue, EXPECTED_TOTAL, finalValue == EXPECTED_TOTAL ? "EXACT" : "MISMATCH");
    }

    private static void sleepQuietly(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void measureLag(Server[] servers) {
        long[] lagsNanos = new long[LAG_PROBES];
        int measured = 0;
        RandomGenerator random = RandomGenerator.getDefault();

        for (int i = 0; i < LAG_PROBES; i++) {
            Server server = servers[i % servers.length];
            RedisClient redis = server.redis();

            // If we fire the marker right after the previous probe was
            // reflected by a flush, every marker lands right at the start
            // of a fresh flush cycle and the measured lag skews toward
            // FLUSH_INTERVAL_MS (worst case). Wait a random amount within
            // [0, FLUSH_INTERVAL_MS) so markers land at different phases of
            // the flush cycle, giving a more realistic distribution.
            sleepQuietly(random.nextLong(FLUSH_INTERVAL_MS));

            long baseline = readCounter(redis);
            long t0 = System.nanoTime();
            server.adder().increment();

            long observedAt = pollUntilIncreased(redis, baseline);
            if (observedAt < 0) {
                System.err.println("Probe " + i + " timeout - Redis did not reflect it within " + LAG_POLL_TIMEOUT_MS + "ms");
                continue;
            }
            lagsNanos[measured++] = observedAt - t0;
        }

        if (measured == 0) {
            System.out.println("No lag probe succeeded.");
            return;
        }

        long[] samples = Arrays.copyOf(lagsNanos, measured);
        Arrays.sort(samples);
        System.out.printf("Lag probes:   %d/%d succeeded%n", measured, LAG_PROBES);
        System.out.printf("Lag min:      %.1f ms%n", samples[0] / 1_000_000.0);
        System.out.printf("Lag p50:      %.1f ms%n", percentileMillis(samples, 0.50));
        System.out.printf("Lag p99:      %.1f ms%n", percentileMillis(samples, 0.99));
        System.out.printf("Lag max:      %.1f ms%n", samples[samples.length - 1] / 1_000_000.0);
        System.out.printf("(theoretical bound: up to ~flushIntervalMs=%dms + 1 incrBy round trip)%n", FLUSH_INTERVAL_MS);
    }

    private static long pollUntilIncreased(RedisClient redis, long baseline) {
        long deadline = System.nanoTime() + LAG_POLL_TIMEOUT_MS * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (readCounter(redis) > baseline) {
                return System.nanoTime();
            }
            try {
                Thread.sleep(LAG_POLL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return -1;
            }
        }
        return -1;
    }

    private static long readCounter(RedisClient redis) {
        String raw = redis.get(KEY);
        return raw == null ? 0L : Long.parseLong(raw);
    }

    private static double percentileMillis(long[] sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.length) - 1;
        index = Math.clamp(index, 0, sortedNanos.length - 1);
        return sortedNanos[index] / 1_000_000.0;
    }
}
