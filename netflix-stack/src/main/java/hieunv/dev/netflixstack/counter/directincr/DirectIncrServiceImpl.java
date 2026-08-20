package hieunv.dev.netflixstack.counter.directincr;

import hieunv.dev.netflixstack.counter.directincr.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.directincr.dto.LatencyStats;
import hieunv.dev.netflixstack.counter.directincr.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.directincr.dto.ValueResponse;
import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.dto.ThroughputResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

/**
 * See {@link DirectIncrService} for the architecture this implements.
 *
 * One {@link RedisClient} per simulated server, created at startup and
 * reused for the life of the application - matches how a real app would
 * hold one pooled Redis client per instance, not one connection per request.
 */
@Service
public class DirectIncrServiceImpl implements DirectIncrService {

    private static final String KEY = "video:123";

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${netflix-stack.counter.server-count:3}")
    private int serverCount;

    private RedisClient[] servers;

    @PostConstruct
    void init() {
        servers = new RedisClient[serverCount];
        for (int i = 0; i < serverCount; i++) {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(50);
            servers[i] = RedisClient.builder()
                    .hostAndPort(redisHost, redisPort)
                    .poolConfig(poolConfig)
                    .build();
        }
    }

    @PreDestroy
    void shutdown() {
        for (RedisClient server : servers) {
            server.close();
        }
    }

    @Override
    public IncrementResponse increment(int serverId) {
        RedisClient server = serverAt(serverId);
        long t0 = System.nanoTime();
        long value = server.incr(KEY);
        double latencyMs = (System.nanoTime() - t0) / 1_000_000.0;
        return new IncrementResponse(serverId, value, latencyMs);
    }

    @Override
    public ValueResponse currentValue() {
        return new ValueResponse(readCounter(servers[0]));
    }

    @Override
    public void reset() {
        servers[0].del(KEY);
    }

    @Override
    public LoadTestResult runLoadTest(int serverId, LoadTestRequest request) {
        RedisClient server = serverAt(serverId);
        int threads = request.threadsOrDefault(20);
        int warmupOps = request.warmupOpsPerThreadOrDefault(200);
        int opsPerThread = request.opsPerThreadOrDefault(5_000);
        long expectedTotal = (long) threads * opsPerThread;

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        long[][] latenciesByThread = new long[threads][];

        for (int t = 0; t < threads; t++) {
            int idx = t;
            Thread worker = new Thread(
                    () -> runWorker(server, warmupOps, opsPerThread, ready, start, done, latenciesByThread, idx),
                    "direct-incr-load-test-" + serverId + "-" + t);
            worker.start();
        }

        await(ready);
        server.del(KEY);
        long startedAt = System.nanoTime();
        start.countDown();
        await(done);
        long elapsedNanos = System.nanoTime() - startedAt;

        return buildResult(serverId, elapsedNanos, latenciesByThread, expectedTotal, server);
    }

    private void runWorker(RedisClient server, int warmupOps, int opsPerThread, CountDownLatch ready,
                            CountDownLatch start, CountDownLatch done, long[][] latenciesByThread, int idx) {
        try {
            for (int i = 0; i < warmupOps; i++) {
                server.incr(KEY);
            }

            ready.countDown();
            await(start);

            long[] latencies = new long[opsPerThread];
            for (int i = 0; i < opsPerThread; i++) {
                long t0 = System.nanoTime();
                server.incr(KEY);
                latencies[i] = System.nanoTime() - t0;
            }
            latenciesByThread[idx] = latencies;
        } catch (RuntimeException e) {
            latenciesByThread[idx] = new long[0];
        } finally {
            done.countDown();
        }
    }

    private LoadTestResult buildResult(int serverId, long elapsedNanos, long[][] latenciesByThread,
                                        long expectedTotal, RedisClient server) {
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
        ThroughputResult throughput = new ThroughputResult(completedOps, elapsedSeconds, completedOps / elapsedSeconds);
        LatencyStats latency = all.length == 0
                ? new LatencyStats(0, 0, 0, 0)
                : new LatencyStats(percentileMillis(all, 0.50), percentileMillis(all, 0.90),
                        percentileMillis(all, 0.99), all[all.length - 1] / 1_000_000.0);

        long finalValue = readCounter(server);
        return new LoadTestResult(serverId, throughput, latency, finalValue, expectedTotal, finalValue == expectedTotal);
    }

    private RedisClient serverAt(int serverId) {
        if (serverId < 0 || serverId >= servers.length) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Unknown serverId " + serverId + " (valid range: 0.." + (servers.length - 1) + ")");
        }
        return servers[serverId];
    }

    private static long readCounter(RedisClient server) {
        String raw = server.get(KEY);
        return raw == null ? 0L : Long.parseLong(raw);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static double percentileMillis(long[] sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.length) - 1;
        index = Math.clamp(index, 0, sortedNanos.length - 1);
        return sortedNanos[index] / 1_000_000.0;
    }
}
