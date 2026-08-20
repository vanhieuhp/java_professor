package hieunv.dev.netflixstack.counter;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class CounterBenchmark {
    private static final int THREADS = 100;
    private static final int INCREMENTS_PER_THREAD = 100_000;
    private static final int RUNS = 10;
    private static final long EXPECTED = (long) THREADS * INCREMENTS_PER_THREAD;

    interface Counter {
        void increment();

        long get();
    }

    static class UnsafeLongCounter implements Counter {
        private long value = 0;

        @Override
        public void increment() {
            value++;
        }

        @Override
        public long get() {
            return value;
        }
    }

    static class SynchronizedCounter implements Counter {
        private long value = 0;

        @Override
        public synchronized void increment() {
            value++;
        }

        @Override
        public synchronized long get() {
            return value;
        }
    }

    static class AtomicLongCounter implements Counter {
        private final AtomicLong value = new AtomicLong(0);

        @Override
        public void increment() {
            value.incrementAndGet();
        }

        @Override
        public long get() {
            return value.get();
        }
    }

    static class LongAdderCounter implements Counter {
        private final LongAdder value = new LongAdder();

        @Override
        public void increment() {
            value.increment();
        }

        @Override
        public long get() {
            return value.sum();
        }
    }

    static void main(String[] args) throws InterruptedException {
        System.out.printf("Threads: %d, increments/thread: %,d, expected: %,d%n%n", THREADS, INCREMENTS_PER_THREAD, EXPECTED);
        System.out.println("Implementation | Run | Result | Lost | Time (ms)");
        System.out.println("---------------|-----|--------|------|----------");

        benchmark("long", UnsafeLongCounter::new);
        benchmark("synchronized", SynchronizedCounter::new);
        benchmark("AtomicLong", AtomicLongCounter::new);
        benchmark("LongAdder", LongAdderCounter::new);
    }

    private static void benchmark(String name, CounterFactory factory) throws InterruptedException {
        for (int run = 1; run <= RUNS; run++) {
            Counter counter = factory.create();
            long start = System.nanoTime();
            runConcurrentIncrement(counter);
            long elapsedNanos = System.nanoTime() - start;
            long result = counter.get();
            System.out.printf("%-14s | %3d | %,10d | %,10d | %8.3f%n",
                    name, run, result, EXPECTED - result, elapsedNanos / 1_000_000.0);
        }
    }

    private static void runConcurrentIncrement(Counter counter) throws InterruptedException {
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    start.await();
                    for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                        counter.increment();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
            thread.start();
        }
        ready.await();
        start.countDown();
        done.await();
    }

    interface CounterFactory {
        Counter create();
    }
}
