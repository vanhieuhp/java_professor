package multithread.codelab.tokenbucket;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class LatencyStats {

    private final long[] latenciesMs;
    private final AtomicInteger index = new AtomicInteger();

    public LatencyStats(int capacity) {
        latenciesMs = new long[capacity];
    }

    public void record(long latencyMs) {
        int i = index.getAndIncrement();
        if (i < latenciesMs.length) {
            latenciesMs[i] = latencyMs;
        }
    }

    public long percentile(double p) {
        int count = Math.min(index.get(), latenciesMs.length);
        long[] copy = Arrays.copyOf(latenciesMs, count);
        Arrays.sort(copy);
        int idx = (int) Math.ceil(p / 100 * count) - 1;
        return copy[Math.max(0, idx)];
    }

    public long p50() {
        return percentile(50);
    }

    public long p95() {
        return percentile(95);
    }

    public long p99() {
        return percentile(99);
    }

    public long p100() {
        return percentile(100);
    }

    public double mean() {
        int count = Math.min(index.get(), latenciesMs.length);
        long sum = 0;
        for (int i = 0; i < count; i++) sum += latenciesMs[i];
        return count == 0 ? 0 : (double) sum / count;
    }
}
