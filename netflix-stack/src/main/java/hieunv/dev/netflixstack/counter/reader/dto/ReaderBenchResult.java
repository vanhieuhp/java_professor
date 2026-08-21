package hieunv.dev.netflixstack.counter.reader.dto;

import java.util.List;

/**
 * One reader measured over a burst of sequential reads.
 *
 * <p>{@code distinctValues} is the point of running the same read many times:
 * a reader that answers from a cache keeps returning the value the cache was
 * filled with, so a burst that straddles a TTL expiry or a rollup pass shows
 * up here as more than one value - the staleness is visible in the data, not
 * just in the latency.
 *
 * @param iterations     reads that were timed (warmup reads are excluded)
 * @param warmup         untimed reads issued first, to pay for JIT and pool setup
 * @param distinctValues every value the burst saw, in the order first seen
 * @param stable         true when the burst never changed its answer
 */
public record ReaderBenchResult(String counterId,
                                String mode,
                                int iterations,
                                int warmup,
                                long value,
                                List<Long> distinctValues,
                                boolean stable,
                                double elapsedMs,
                                LatencyStats latency) {
}
