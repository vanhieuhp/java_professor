package multithread.codelab.ratelimit;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class RateLimiterDemo {

    private static void runWithoutLimiter(CountDownLatch startSignal, int numPayments) throws InterruptedException {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        Thread[] threads = new Thread[numPayments];
        for (int i = 0; i < numPayments; i++) {
            final String txnId = "txn-" + i;
            threads[i] = new Thread(() -> {
                try {
                    startSignal.await();
                    ExternalPaymentAPI.call(txnId);
                    success.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();;
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  WITHOUT limiter: %d succeeded, %d failed, elapsed=%dms%n",
                success.get(), failed.get(), elapsed);
        System.out.printf("  Throughput: %.1f TPS%n", success.get() * 1000.0 / elapsed);
    }

    private static void runWithLimiter(CountDownLatch startSignal, Semaphore limiter, int numPayments) throws InterruptedException {
        AtomicInteger success = new AtomicInteger(0);
        AtomicInteger rejected = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        long start = System.currentTimeMillis();

        Thread[] threads = new Thread[numPayments];
        for (int i = 0; i < numPayments; i++) {
            final String txnId = "txn-" + i;
            threads[i] = new Thread(() -> {
                try {
                    startSignal.await();

                    if (!limiter.tryAcquire()) {
                        rejected.incrementAndGet();
                        return; // fail fast - don't block
                    }

                    try {
                        ExternalPaymentAPI.call(txnId);
                        success.incrementAndGet();
                    } finally {
                        limiter.release(); // ALWAYS release the semaphore
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    failed.incrementAndGet();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            t.join();
        }
        long elapsed = System.currentTimeMillis() - start;
        System.out.printf("  WITH limiter: %d succeeded, %d rejected, %d failed, elapsed=%dms%n",
                success.get(), rejected.get(), failed.get(), elapsed);
        System.out.printf("  Throughput: %.1f TPS%n", success.get() * 1000.0 / elapsed);
    }

    public static void main(String[] args) throws InterruptedException {
        int NUM_PAYMENTS = 100;
        CountDownLatch startSignal = new CountDownLatch(1);
        startSignal.countDown();
        System.out.println("=== Payment API Rate Limiter Demo ===");
        System.out.printf("Simulating %d concurrent payments, API capacity=20, latency=200ms%n%n",
                NUM_PAYMENTS);

        System.out.println("Run 1: WITHOUT rate limiter");
        System.out.println("(All 100 threads call API simultaneously — expect API errors under load)");
        runWithoutLimiter(startSignal, NUM_PAYMENTS);

        Thread.sleep(1000);
        System.out.println("\nRun 2: WITH Semaphore(20) — max 20 concurrent API calls");
        System.out.println("(100 threads contend for 20 permits; 80 fail-fast)");
        startSignal = new CountDownLatch(1);
        startSignal.countDown(); // reset by creating new latch
        Semaphore limiter = new Semaphore(20, true); // fair=true: FIFO queuing
        runWithLimiter(startSignal, limiter, NUM_PAYMENTS);
    }
}
