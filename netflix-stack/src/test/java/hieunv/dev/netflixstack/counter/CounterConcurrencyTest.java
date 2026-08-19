package hieunv.dev.netflixstack.counter;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class CounterConcurrencyTest {
    private static final int THREADS = 100;
    private static final int INCREMENTS_PER_THREAD = 10_000;
    private static final long EXPECTED = (long) THREADS * INCREMENTS_PER_THREAD;

    @Test
    void naiveCounterLosesUpdatesUnderContention() {
        Counter counter = new Counter();

        runConcurrentIncrements(counter::add);

        assertThat(counter.get()).isLessThan(EXPECTED);
    }

    @Test
    void synchronizedCounterKeepsTheExactValue() {
        SynchronizedCounter counter = new SynchronizedCounter();

        runConcurrentIncrements(counter::add);

        assertThat(counter.get()).isEqualTo(EXPECTED);
    }

    @Test
    void atomicLongCounterKeepsTheExactValue() {
        AtomicLongCounter counter = new AtomicLongCounter();

        runConcurrentIncrements(counter::add);

        assertThat(counter.get()).isEqualTo(EXPECTED);
    }

    @Test
    void longAdderCounterKeepsTheExactValue() {
        LongAdderCounter counter = new LongAdderCounter();

        runConcurrentIncrements(counter::add);

        assertThat(counter.get()).isEqualTo(EXPECTED);
    }

    @Test
    void compareThroughputForAllStrategies() {
        Counter naiveCounter = new Counter();
        SynchronizedCounter synchronizedCounter = new SynchronizedCounter();
        AtomicLongCounter atomicLongCounter = new AtomicLongCounter();
        LongAdderCounter longAdderCounter = new LongAdderCounter();

        Measurement naive = measure("long", naiveCounter::add, naiveCounter::get);
        Measurement synchronizedMeasurement = measure("synchronized", synchronizedCounter::add, synchronizedCounter::get);
        Measurement atomicLong = measure("AtomicLong", atomicLongCounter::add, atomicLongCounter::get);
        Measurement longAdder = measure("LongAdder", longAdderCounter::add, longAdderCounter::get);

        assertThat(naive.name()).isEqualTo("long");
        assertThat(synchronizedMeasurement.name()).isEqualTo("synchronized");
        assertThat(atomicLong.name()).isEqualTo("AtomicLong");
        assertThat(longAdder.name()).isEqualTo("LongAdder");
    }

    private static void runConcurrentIncrements(LongConsumer add) {
        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ExecutorService executor = Executors.newFixedThreadPool(THREADS);
            CountDownLatch ready = new CountDownLatch(THREADS);
            CountDownLatch start = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(THREADS);

            try {
                for (int i = 0; i < THREADS; i++) {
                    executor.execute(() -> {
                        ready.countDown();
                        await(start);
                        for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                            add.accept(1);
                        }
                        done.countDown();
                    });
                }

                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                assertThat(done.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                executor.shutdownNow();
            }
        });
    }

    private static Measurement measure(String name, LongConsumer add, LongSupplier get) {
        long startedAt = System.nanoTime();
        runConcurrentIncrements(add);
        long elapsedNanos = System.nanoTime() - startedAt;
        long operationsPerSecond = (EXPECTED * 1_000_000_000L) / elapsedNanos;
        return new Measurement(name, get.getAsLong(), operationsPerSecond);
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private record Measurement(String name, long value, long operationsPerSecond) {
    }
}
