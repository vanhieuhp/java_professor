package hieunv.dev.netflixstack.counter.redis;

import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

public class DirectIncrBenchmark {

    private static final String REDIS_HOST = System.getProperty("redis.host", "localhost");
    private static final int REDIS_PORT = Integer.getInteger("redis.port", 6379);
    private static final String KEY = "video:123";

    private static final int SERVERS = Integer.getInteger("servers", 3);
    private static final int THREADS_PER_SERVER = Integer.getInteger("threadsPerServer", 20);
    private static final int WARMUP_OPS_PER_THREAD = Integer.getInteger("warmupOps", 200);
    private static final int OPS_PER_THREAD = Integer.getInteger("opsPerThread", 5_000);

    private static final int TOTAL_THREADS = SERVERS * THREADS_PER_SERVER;
    private static final long EXPECTED_TOTAL = (long) TOTAL_THREADS * OPS_PER_THREAD;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("Kien truc A - Direct INCR");
        System.out.printf("servers=%d threadsPerServer=%d opsPerThread=%d totalOps=%,d redis=%s:%d%n%n",
                SERVERS, THREADS_PER_SERVER, OPS_PER_THREAD, EXPECTED_TOTAL, REDIS_HOST, REDIS_PORT);

        RedisClient[] servers = new RedisClient[SERVERS];
        for (int s = 0; s < SERVERS; s++) {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(THREADS_PER_SERVER);
            poolConfig.setMinIdle(THREADS_PER_SERVER);
            servers[s] = RedisClient.builder()
                    .hostAndPort(REDIS_HOST, REDIS_PORT)
                    .poolConfig(poolConfig)
                    .build();
        }

        CountDownLatch ready = new CountDownLatch(TOTAL_THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(TOTAL_THREADS);
        long[][] latenciesByThread = new long[TOTAL_THREADS][];

        int threadIndex = 0;
        for (int s = 0; s < SERVERS; s++) {
            RedisClient server = servers[s];
            for (int t = 0; t < THREADS_PER_SERVER; t++) {
                int idx = threadIndex++;
                Thread worker = new Thread(
                        () -> runWorker(server, ready, start, done, latenciesByThread, idx),
                        "server-%d-thread-%d".formatted(s, t));
                worker.start();
            }
        }

        ready.await();
        servers[0].del(KEY);
        long startedAt = System.nanoTime();
        start.countDown();
        done.await();
        long elapsedNanos = System.nanoTime() - startedAt;

        report(elapsedNanos, latenciesByThread, servers[0]);

        for (RedisClient server : servers) {
            server.close();
        }
    }

    private static void runWorker(RedisClient server, CountDownLatch ready, CountDownLatch start,
                                   CountDownLatch done, long[][] latenciesByThread, int idx) {
        try {
            for (int i = 0; i < WARMUP_OPS_PER_THREAD; i++) {
                server.incr(KEY);
            }

            ready.countDown();
            await(start);

            long[] latencies = new long[OPS_PER_THREAD];
            for (int i = 0; i < OPS_PER_THREAD; i++) {
                long t0 = System.nanoTime();
                server.incr(KEY);
                latencies[i] = System.nanoTime() - t0;
            }
            latenciesByThread[idx] = latencies;
        } catch (RuntimeException e) {
            System.err.println("Thread " + idx + " that bai: " + e);
            latenciesByThread[idx] = new long[0];
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

    private static void report(long elapsedNanos, long[][] latenciesByThread, RedisClient check) {
        int completedOps = 0;
        for (long[] latencies : latenciesByThread) {
            completedOps += latencies.length;
        }

        long[] all = new long[completedOps];
        int pos = 0;
        for (long[] latencies : latenciesByThread) {
            System.arraycopy(latencies, 0, all, pos, latencies.length);
            pos += latencies.length;
        }
        Arrays.sort(all);

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        double throughput = completedOps / elapsedSeconds;

        System.out.printf("Elapsed:       %,.3f s%n", elapsedSeconds);
        System.out.printf("Completed ops: %,d / %,d%n", completedOps, EXPECTED_TOTAL);
        System.out.printf("Throughput:    %,.0f ops/sec%n", throughput);
        if (all.length > 0) {
            System.out.printf("Latency p50:   %.3f ms%n", percentileMillis(all, 0.50));
            System.out.printf("Latency p90:   %.3f ms%n", percentileMillis(all, 0.90));
            System.out.printf("Latency p99:   %.3f ms%n", percentileMillis(all, 0.99));
            System.out.printf("Latency max:   %.3f ms%n", all[all.length - 1] / 1_000_000.0);
        }

        long finalValue = Long.parseLong(check.get(KEY));
        System.out.printf("%nFinal counter value: %,d (expected %,d) -> %s%n",
                finalValue, EXPECTED_TOTAL, finalValue == EXPECTED_TOTAL ? "CHINH XAC" : "SAI LECH");
    }

    private static double percentileMillis(long[] sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.length) - 1;
        index = Math.clamp(index, 0, sortedNanos.length - 1);
        return sortedNanos[index] / 1_000_000.0;
    }
}
