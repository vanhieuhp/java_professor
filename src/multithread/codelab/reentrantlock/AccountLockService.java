package multithread.codelab.reentrantlock;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class AccountLockService {
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public boolean transferWithTimeout(
            String fromId, String toId, double amount, long timeoutMs
    ) {
        ReentrantLock lockA = locks.computeIfAbsent(fromId, k -> new ReentrantLock());
        ReentrantLock lockB = locks.computeIfAbsent(toId, k -> new ReentrantLock());

        ReentrantLock first = fromId.compareTo(toId) < 0 ? lockA : lockB;
        ReentrantLock second = fromId.compareTo(toId) < 0 ? lockB : lockA;

        try {
            if (!first.tryLock(timeoutMs, TimeUnit.MICROSECONDS)) {
                return false;
            }

            try {
                if (!second.tryLock(timeoutMs, TimeUnit.MICROSECONDS)) {
                    return false;
                }

                try {
                    doTransfer(fromId, toId, amount);
                    return true;
                } finally {
                    second.unlock();
                }
            } finally {
                first.unlock();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private  void doTransfer(String fromId, String toId, double amount) {
        System.out.println("do transfer: " + fromId + " -> " + toId + " " + amount + "");
    }
}
