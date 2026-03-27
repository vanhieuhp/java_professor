import java.util.concurrent.locks.ReentrantLock;

public class FraudDetectionService {

    private static final Object LOCK = new Object();
    private int checkCount = 0;
    private ReentrantLock lock = new ReentrantLock();

    public boolean isFraudulent(Transaction tx) throws InterruptedException {
//        synchronized (LOCK) {
//            checkCount++;
//
//            boolean result = callExternalFraudApi(tx);
//            return result;
//        }
        boolean result = callExternalFraudApi(tx);
        lock.lock();
        try {
            checkCount++;
            return result;
        } finally {
            lock.unlock();
        }
    }

    private boolean callExternalFraudApi(Transaction tx) throws InterruptedException {
        // Simulate HTTP call
        Thread.sleep(200);
        return tx.amount > 10_000_000;
    }

    record Transaction(double amount) { }

}
