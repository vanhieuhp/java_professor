package multithread.codelab.tokenbucket;

import java.util.concurrent.locks.ReentrantLock;

public class TokenBucket {

    private final double ratePerSecond;
    private final double burstCapacity;

    private double tokens;
    private long lastRefillNanos;

    private final ReentrantLock lock = new ReentrantLock();
    public TokenBucket(double ratePerSecond, double burstCapacity) {
        this.ratePerSecond = ratePerSecond;
        this.burstCapacity = burstCapacity;
        this.tokens = burstCapacity; // start with a full bucket
        this.lastRefillNanos = System.nanoTime();
    }

    private void refill() {
        long now = System.nanoTime();
        double elapsed = (now - lastRefillNanos) / 1_000_000_000.0; //second
        double newTokens = elapsed * ratePerSecond;
        tokens = Math.min(burstCapacity, tokens + newTokens);
        lastRefillNanos = now;
    }

    public boolean tryAcquire() {
        lock.lock();
        try {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    public void acquire() throws InterruptedException {
        while (true) {
            lock.lock();
            try {
                refill();
                if (tokens >= 1.0) {
                    tokens -= 1.0;
                    return;
                }
                // Calculate exact await time until next tokens arrives
                double deficit = 1.0 - tokens;
                long waitMs = (long) Math.ceil(deficit / ratePerSecond * 1000);
                lock.unlock();
                Thread.sleep(Math.max(1, waitMs));
                lock.lock(); // re-acquire lock for next loop iteration check
            } finally {
                if (lock.isHeldByCurrentThread()) {
                    lock.unlock();
                }
            }
        }
    }

    public double getTokens() {
        lock.lock();
        try {
            refill();
            return tokens;
        } finally {
            lock.unlock();
        }
    }
}
