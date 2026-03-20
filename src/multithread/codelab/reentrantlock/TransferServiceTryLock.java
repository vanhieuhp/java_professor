package multithread.codelab.reentrantlock;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class TransferServiceTryLock {

    private final AtomicLong succeeded = new AtomicLong();
    private final AtomicLong timedOut = new AtomicLong();

    private final long timeoutMs;

    public TransferServiceTryLock(long timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public void transfer(Account from, Account to, long amount) throws InterruptedException {
        while (true) {
            // Step 1: try to acquire the first lock
            if (from.lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                try {
                    // Step 2: try to acquire the second lock while holding the first one
                    if (to.lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)) {
                        try {
                            // Got both locks - do the work
                            if (from.debit(amount)) {
                                to.credit(amount);
                            }
                            succeeded.incrementAndGet();
                            return;// success - exit the loop
                        } finally {
                            to.lock.unlock(); // always release in finally
                        }
                    } else {
                        // Timed out on lock 2 - release lock 1 and retry
                        timedOut.incrementAndGet();
                    }
                } finally {
                    from.lock.unlock(); // always release in finally!
                }
            } else {
                timedOut.incrementAndGet();
            }

            // randomized backoff  - prevents livelock where both threads
            // Retry in perfect sync and keep colliding
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, timeoutMs + 1));
        }
    }

    public long getSucceeded() { return succeeded.get(); }
    public long getTimedOut()  { return timedOut.get();  }
}
