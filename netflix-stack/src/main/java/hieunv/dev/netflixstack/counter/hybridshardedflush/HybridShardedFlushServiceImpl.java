package hieunv.dev.netflixstack.counter.hybridshardedflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.dto.ThroughputResult;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ChaosSlowRequest;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ValueResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import redis.clients.jedis.ConnectionPoolConfig;
import redis.clients.jedis.RedisClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;


@RequiredArgsConstructor
@Service
public class HybridShardedFlushServiceImpl implements HybridShardedFlushService {

    private static final String KEY_PREFIX = "video:123:shard:";

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${netflix-stack.counter.server-count:3}")
    private int serverCount;

    @Value("${netflix-stack.counter.flush-interval-ms:100}")
    private long flushIntervalMs;

    @Value("${netflix-stack.counter.max-pending:2000}")
    private long maxPending;

    @Value("${netflix-stack.counter.circuit-breaker.failure-rate-threshold:50}")
    private float cbFailureRateThreshold;

    @Value("${netflix-stack.counter.circuit-breaker.wait-duration-in-open-state-ms:2000}")
    private long cbWaitDurationInOpenStateMs;

    @Value("${netflix-stack.counter.circuit-breaker.permitted-calls-in-half-open:3}")
    private int cbPermittedCallsInHalfOpen;

    @Value("${netflix-stack.counter.circuit-breaker.sliding-window-size:10}")
    private int cbSlidingWindowSize;

    @Value("${netflix-stack.counter.chaos.call-timeout-ms:300}")
    private long callTimeoutMs;

    private ServerContext[] servers;
    private ScheduledExecutorService[] flushers;

    private record ServerContext(int index, String shardKey, LongAdder adder, AtomicLong pendingRetry,
                                 AtomicLong dropped, ChaosRedis redis, CircuitBreaker circuitBreaker) {
    }

    private enum ChaosMode {
        NONE, DEAD, SLOW
    }

    private static final class RedisChaosException extends RuntimeException {
        RedisChaosException(String message) {
            super(message);
        }
    }


    private static final class ChaosRedis {
        private final RedisClient delegate;
        private final long callTimeoutMs;
        private volatile ChaosMode mode = ChaosMode.NONE;
        private volatile long slowDelayMs;

        private ChaosRedis(RedisClient delegate, long callTimeoutMs) {
            this.delegate = delegate;
            this.callTimeoutMs = callTimeoutMs;
        }

        void induceDead() {
            mode = ChaosMode.DEAD;
        }

        void induceSlow(long delayMs) {
            slowDelayMs = delayMs;
            mode = ChaosMode.SLOW;
        }

        void recover() {
            mode = ChaosMode.NONE;
        }

        long incrBy(String key, long delta) {
            ChaosMode current = mode;
            if (current == ChaosMode.DEAD) {
                throw new RedisChaosException("simulated Redis outage (dead)");
            }
            if (current == ChaosMode.SLOW) {
                long delay = slowDelayMs;
                if (delay >= callTimeoutMs) {
                    sleepQuietly(callTimeoutMs);
                    throw new RedisChaosException("simulated client-side timeout after " + callTimeoutMs
                            + "ms (call would have taken " + delay + "ms)");
                }
                sleepQuietly(delay);
            }
            return delegate.incrBy(key, delta);
        }

        String get(String key) {
            return delegate.get(key);
        }

        void del(String key) {
            delegate.del(key);
        }

        void close() {
            delegate.close();
        }
    }

    @PostConstruct
    void init() {
        CircuitBreakerConfig cbConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(cbFailureRateThreshold)
                .waitDurationInOpenState(Duration.ofMillis(cbWaitDurationInOpenStateMs))
                .permittedNumberOfCallsInHalfOpenState(cbPermittedCallsInHalfOpen)
                .slidingWindowSize(cbSlidingWindowSize)
                .build();

        servers = new ServerContext[serverCount];
        flushers = new ScheduledExecutorService[serverCount];
        for (int i = 0; i < serverCount; i++) {
            ConnectionPoolConfig poolConfig = new ConnectionPoolConfig();
            poolConfig.setMaxTotal(2);
            RedisClient redisClient = RedisClient.builder()
                    .hostAndPort(redisHost, redisPort)
                    .poolConfig(poolConfig)
                    .build();

            int index = i;
            CircuitBreaker circuitBreaker = CircuitBreaker.of("server-" + index, cbConfig);
            circuitBreaker.getEventPublisher().onStateTransition(event ->
                    System.out.printf("[circuit-breaker] server-%d: %s%n", index, event.getStateTransition()));

            ServerContext server = new ServerContext(index, KEY_PREFIX + index, new LongAdder(), new AtomicLong(0),
                    new AtomicLong(0), new ChaosRedis(redisClient, callTimeoutMs), circuitBreaker);
            servers[i] = server;

            ScheduledExecutorService flusher = Executors.newSingleThreadScheduledExecutor(
                    r -> new Thread(r, "hybrid-flush-server-" + index));
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
        return new IncrementResponse(serverId, server.shardKey(), server.adder().sum());
    }

    @Override
    public StatusResponse status(int serverId) {
        ServerContext server = serverAt(serverId);
        return new StatusResponse(serverId, server.shardKey(), server.circuitBreaker().getState().toString(),
                server.pendingRetry().get(), server.dropped().get(), server.adder().sum(),
                readCounter(server.redis(), server.shardKey()));
    }

    @Override
    public ValueResponse currentValue() {
        Map<Integer, Long> shards = new LinkedHashMap<>();
        long total = 0;
        for (ServerContext server : servers) {
            long value = readCounter(server.redis(), server.shardKey());
            shards.put(server.index(), value);
            total += value;
        }
        return new ValueResponse(total, shards);
    }

    @Override
    public void induceDeadChaos(int serverId) {
        serverAt(serverId).redis().induceDead();
    }

    @Override
    public void induceSlowChaos(int serverId, ChaosSlowRequest request) {
        serverAt(serverId).redis().induceSlow(request.delayMs());
    }

    @Override
    public void recoverChaos(int serverId) {
        serverAt(serverId).redis().recover();
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
                    "hybrid-load-test-" + serverId + "-" + t);
            worker.start();
        }

        await(ready);
        synchronized (server) {
            server.adder().reset();
            server.pendingRetry().set(0);
            server.dropped().set(0);
        }
        server.redis().del(server.shardKey());

        long startedAt = System.nanoTime();
        start.countDown();
        await(done);
        long elapsedNanos = System.nanoTime() - startedAt;

        // The background flusher (still running) needs a few ticks to drain
        // whatever the last measured increments left behind.
        sleepQuietly(flushIntervalMs * 3);

        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        ThroughputResult throughput = new ThroughputResult(expectedTotal, elapsedSeconds, expectedTotal / elapsedSeconds);
        long finalValue = readCounter(server.redis(), server.shardKey());
        return new LoadTestResult(serverId, server.shardKey(), throughput, finalValue, expectedTotal,
                finalValue == expectedTotal);
    }

    private void flush(ServerContext server) {
        synchronized (server) {
            long newDelta = server.adder().sumThenReset();
            long toSend = server.pendingRetry().getAndSet(0) + newDelta;
            if (toSend <= 0) {
                return;
            }
            try {
                server.circuitBreaker().executeRunnable(() -> server.redis().incrBy(server.shardKey(), toSend));
            } catch (Exception e) {
                onFlushFailure(server, toSend, e);
            }
        }
    }

    private void onFlushFailure(ServerContext server, long amount, Exception cause) {
        AtomicLong pending = server.pendingRetry();
        long capacity = Math.max(0, maxPending - pending.get());
        long accepted = Math.min(amount, capacity);
        long dropped = amount - accepted;

        if (accepted > 0) {
            pending.addAndGet(accepted);
        }
        if (dropped > 0) {
            server.dropped().addAndGet(dropped);
            System.err.printf("[server-%d] dropped %,d (pendingRetry at cap %,d) - %s: %s%n",
                    server.index(), dropped, maxPending, cause.getClass().getSimpleName(), cause.getMessage());
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

    private static long readCounter(ChaosRedis redis, String key) {
        String raw = redis.get(key);
        return raw == null ? 0L : Long.parseLong(raw);
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
