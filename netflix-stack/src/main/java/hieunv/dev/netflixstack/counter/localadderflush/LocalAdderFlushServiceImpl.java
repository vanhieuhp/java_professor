package hieunv.dev.netflixstack.counter.localadderflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.dto.ThroughputResult;
import hieunv.dev.netflixstack.counter.localadderflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.localadderflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.ValueResponse;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

/**
 * See {@link LocalAdderFlushService} for the architecture this implements.
 *
 * Each server gets its own single-thread scheduled flusher (started at
 * {@link PostConstruct}, stopped at {@link PreDestroy}) instead of Spring's
 * {@code @Scheduled}, so one server's flush cadence can never be delayed by
 * another's - Spring's default task scheduler is single-threaded and would
 * serialize all servers' flushes through one thread.
 */
@Service
public class LocalAdderFlushServiceImpl implements LocalAdderFlushService {

    private static final String KEY = "video:123";

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${netflix-stack.counter.server-count:3}")
    private int serverCount;

    @Value("${netflix-stack.counter.flush-interval-ms:100}")
    private long flushIntervalMs;

    private ServerContext[] servers;
    private ScheduledExecutorService[] flushers;

    private record ServerContext(int index, LongAdder adder, RedisClient redis) {
    }

    @PostConstruct
    void init() {
        servers = new ServerContext[serverCount];
        flushers = new ScheduledExecutorService[serverCount];
        for (int i = 0; i < serverCount; i++) {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(2);
            RedisClient redis = RedisClient.builder()
                    .hostAndPort(redisHost, redisPort)
                    .poolConfig(poolConfig)
                    .build();
            ServerContext server = new ServerContext(i, new LongAdder(), redis);
            servers[i] = server;

            int serverIndex = i;
            ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "local-adder-flush-server-" + serverIndex));
            flusher.scheduleAtFixedRate(() -> flush(server), flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
            flushers[i] = flusher;
        }
    }

    @PreDestroy
    void shutdown() {
        for (ScheduledExecutorService flusher : flushers) {
            flusher.shutdown();
        }
        for (ServerContext server : servers) {
            server.redis().close();
        }
    }

    @Override
    public IncrementResponse increment(int serverId) {
        ServerContext server = serverAt(serverId);
        server.adder().increment();
        return new IncrementResponse(serverId, server.adder().sum());
    }

    @Override
    public StatusResponse status(int serverId) {
        ServerContext server = serverAt(serverId);
        return new StatusResponse(serverId, server.adder().sum());
    }

    @Override
    public ValueResponse currentValue() {
        return new ValueResponse(readCounter(servers[0].redis()));
    }

    @Override
    public LoadTestResult runLoadTest(int serverId, LoadTestRequest request) {
        ServerContext server = serverAt(serverId);
        int threads = request.threadsOrDefault(20);
        int warmupOps = request.warmupOpsPerThreadOrDefault(100_000);
        int opsPerThread = request.opsPerThreadOrDefault(1_000_000);
        long expectedTotal = (long) threads * opsPerThread;

        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);

        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(
                    () -> runWorker(server.adder(), warmupOps, opsPerThread, ready, start, done),
                    "local-adder-load-test-" + serverId + "-" + t);
            worker.start();
        }

        // Warmup already filled the adder - clear it (and the shared key)
        // before starting the timed run, otherwise warmup counts would leak
        // into Redis and break the correctness check below.
        await(ready);
        synchronized (server) {
            server.adder().reset();
        }
        server.redis().del(KEY);

        long startedAt = System.nanoTime();
        start.countDown();
        await(done);
        long elapsedNanos = System.nanoTime() - startedAt;

        // The background flusher (still running) needs a few ticks to drain
        // whatever the last measured increments left behind.
        sleepQuietly(flushIntervalMs * 3);

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        ThroughputResult throughput = new ThroughputResult(expectedTotal, elapsedSeconds, expectedTotal / elapsedSeconds);
        long finalValue = readCounter(server.redis());
        return new LoadTestResult(serverId, throughput, finalValue, expectedTotal, finalValue == expectedTotal);
    }

    private void flush(ServerContext server) {
        synchronized (server) {
            long delta = server.adder().sumThenReset();
            if (delta > 0) {
                try {
                    server.redis().incrBy(KEY, delta);
                } catch (RuntimeException e) {
                    System.err.println("[local-adder-flush server-" + server.index() + "] flush failed, lost "
                            + delta + " count: " + e);
                }
            }
        }
    }

    private void runWorker(LongAdder adder, int warmupOps, int opsPerThread, CountDownLatch ready,
                            CountDownLatch start, CountDownLatch done) {
        try {
            for (int i = 0; i < warmupOps; i++) {
                adder.increment();
            }

            ready.countDown();
            await(start);

            for (int i = 0; i < opsPerThread; i++) {
                adder.increment();
            }
        } finally {
            done.countDown();
        }
    }

    private ServerContext serverAt(int serverId) {
        if (serverId < 0 || serverId >= servers.length) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Unknown serverId " + serverId + " (valid range: 0.." + (servers.length - 1) + ")");
        }
        return servers[serverId];
    }

    private static long readCounter(RedisClient redis) {
        String raw = redis.get(KEY);
        return raw == null ? 0L : Long.parseLong(raw);
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
}
