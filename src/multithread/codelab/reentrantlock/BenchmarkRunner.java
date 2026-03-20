package multithread.codelab.reentrantlock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class BenchmarkRunner {
    static final int THREAD_COUNT   = 10;   // concurrent threads
    static final int TRANSFERS_EACH = 200;  // transfers per thread

    public static void main(String[] args) throws InterruptedException {
        long[] timeouts = {1, 5, 20, 50, 100};
        System.out.printf("%-12s %-12s %-12s %-12s %-15s%n",
                "timeout(ms)", "succeeded", "timedOut", "total_ms", "throughput/s");
        System.out.println("-".repeat(65));
        for (long t : timeouts) {
            runBenchmark(t);
        }
    }

    static void runBenchmark(long timeoutMs) throws InterruptedException {
        // Create 5 accounts — transfers happen between random pairs
        Account[] accounts = {
                new Account("A", 10_000),
                new Account("B", 10_000),
                new Account("C", 10_000),
                new Account("D", 10_000),
                new Account("E", 10_000),
        };
        TransferServiceTryLock service = new TransferServiceTryLock(timeoutMs);
        ExecutorService pool = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch ready = new CountDownLatch(THREAD_COUNT);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREAD_COUNT);

        for (int i = 0; i < THREAD_COUNT; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await(); // all threads start simultaneously

                    ThreadLocalRandom rng = ThreadLocalRandom.current();
                    for (int j = 0; j < TRANSFERS_EACH; j++) {
                        int fromIdx = rng.nextInt(accounts.length);
                        int toIdx;
                        do {
                            toIdx = rng.nextInt(accounts.length);
                        } while (fromIdx == toIdx);

                        service.transfer(accounts[fromIdx], accounts[toIdx], 10);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }

        ready.await();
        long startMs = System.currentTimeMillis();
        start.countDown();
        done.await();
        long elapsedMs = System.currentTimeMillis() - startMs;
        pool.shutdown();
        long succeeded = service.getSucceeded();
        long timedOut  = service.getTimedOut();
        double throughput = succeeded * 1000.0 / elapsedMs;
        System.out.printf("%-12d %-12d %-12d %-12d %-15.0f%n",
                timeoutMs, succeeded, timedOut, elapsedMs, throughput);
    }


}
