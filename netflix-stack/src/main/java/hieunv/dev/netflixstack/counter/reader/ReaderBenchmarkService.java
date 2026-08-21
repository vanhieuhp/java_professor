package hieunv.dev.netflixstack.counter.reader;

import hieunv.dev.netflixstack.counter.reader.dto.LatencyStats;
import hieunv.dev.netflixstack.counter.reader.dto.ReadResponse;
import hieunv.dev.netflixstack.counter.reader.dto.ReaderBenchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Runs one reader in a tight sequential loop so the two strategies can be
 * compared on the same counter, back to back.
 *
 * <p>Sequential on purpose: the question is what a single read costs and what
 * it answers, not how many reads the box can sustain. Concurrency would add
 * queueing time to every sample and hide the gap between a Redis hit and a
 * Cassandra scan of the tail - which is exactly the gap worth seeing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReaderBenchmarkService {

    private static final int MAX_ITERATIONS = 100_000;

    /** Keyed by bean name - see the {@code @Service("...")} on each reader. */
    private final Map<String, CounterReaderService> readersByMode;

    public ReadResponse read(String mode, String counterId) {
        CounterReaderService reader = readerFor(mode);
        long t0 = System.nanoTime();
        long value = reader.getCount(counterId);
        return new ReadResponse(counterId, mode, value, (System.nanoTime() - t0) / 1_000_000.0);
    }

    public ReaderBenchResult bench(String mode, String counterId, int iterations, int warmup) {
        CounterReaderService reader = readerFor(mode);
        if (iterations < 1 || iterations > MAX_ITERATIONS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "iterations must be between 1 and " + MAX_ITERATIONS);
        }
        if (warmup < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "warmup must not be negative");
        }

        for (int i = 0; i < warmup; i++) {
            reader.getCount(counterId);
        }

        long[] latencies = new long[iterations];
        List<Long> distinctValues = new ArrayList<>();
        long lastValue = 0L;
        long startedAt = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long t0 = System.nanoTime();
            long value = reader.getCount(counterId);
            latencies[i] = System.nanoTime() - t0;
            lastValue = value;
            if (!distinctValues.contains(value)) {
                distinctValues.add(value);
            }
        }
        double elapsedMs = (System.nanoTime() - startedAt) / 1_000_000.0;

        LatencyStats latency = statsOf(latencies);
        boolean stable = distinctValues.size() == 1;
        log.info("reader-bench[{}] mode={} iterations={} value={} stable={} distinct={} "
                        + "p50={}ms p99={}ms max={}ms",
                counterId, mode, iterations, lastValue, stable, distinctValues,
                latency.p50Ms(), latency.p99Ms(), latency.maxMs());
        if (!stable) {
            log.warn("reader-bench[{}] mode={} answered {} different values in one burst: {} - "
                            + "a cache expiry or a rollup pass landed mid-run",
                    counterId, mode, distinctValues.size(), distinctValues);
        }

        return new ReaderBenchResult(counterId, mode, iterations, warmup, lastValue,
                distinctValues, stable, elapsedMs, latency);
    }

    private CounterReaderService readerFor(String mode) {
        CounterReaderService reader = readersByMode.get(mode);
        if (reader == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown reader mode '" + mode + "' (valid: " + new TreeSet<>(readersByMode.keySet()) + ")");
        }
        return reader;
    }

    private static LatencyStats statsOf(long[] latencyNanos) {
        long[] sorted = latencyNanos.clone();
        Arrays.sort(sorted);
        long total = 0L;
        for (long nanos : sorted) {
            total += nanos;
        }
        return new LatencyStats(
                sorted[0] / 1_000_000.0,
                percentileMillis(sorted, 0.50),
                percentileMillis(sorted, 0.90),
                percentileMillis(sorted, 0.99),
                sorted[sorted.length - 1] / 1_000_000.0,
                total / (double) sorted.length / 1_000_000.0);
    }

    private static double percentileMillis(long[] sortedNanos, double p) {
        int index = (int) Math.ceil(p * sortedNanos.length) - 1;
        index = Math.clamp(index, 0, sortedNanos.length - 1);
        return sortedNanos[index] / 1_000_000.0;
    }
}
