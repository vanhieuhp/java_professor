package java_effective.enforce_singleton.problem;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadSafetyDemo {

    // ============================================================
    // ❌ UNSAFE: Race condition - multiple threads can create instances
    // ============================================================
    static class UnsafeSingleton {
        public static UnsafeSingleton INSTANCE;
        private String id = UUID.randomUUID().toString();

        private UnsafeSingleton() {
            System.out.println("[CONSTRUCTOR] Creating instance with ID: " + id);
        }

        public static UnsafeSingleton getInstance() {
            if (INSTANCE == null) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    INSTANCE = new UnsafeSingleton();
                }
            }
            return INSTANCE;
        }

        public String getId() {
            return id;
        }
    }

    static class SafeSingleton {
        public static SafeSingleton INSTANCE = new SafeSingleton();
        private String id = UUID.randomUUID().toString();

        private SafeSingleton() {
            System.out.println("[CONSTRUCTOR] Creating instance with ID: " + id);
        }

        public static synchronized SafeSingleton getInstance() {
            if (INSTANCE == null) {
                INSTANCE = new SafeSingleton();
            }
            return INSTANCE;
        }

        public String getId() {
            return id;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("DEMO 1: THREAD-SAFETY VULNERABILITY");
        System.out.println("=".repeat(60));

        // --------------------------------------------
        // Test 1: Unsafe Singleton (BROKEN)
        // --------------------------------------------
        System.out.println("\n>>> Testing UNSAFE Singleton with 10 threads:");
        System.out.println("    (Each thread tries to getInstance() simultaneously)\n");

        UnsafeSingleton.INSTANCE = null;  // Reset

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(10);
        Set<String> uniqueIds = new HashSet<>();

        for (int i = 0; i < 10; i++) {
            final int threadNum = i;
            executor.submit(() -> {
                UnsafeSingleton instance = UnsafeSingleton.getInstance();
                synchronized (uniqueIds) {
                    uniqueIds.add(instance.getId());
                }
                System.out.println("Thread " + threadNum + " -> ID: " + instance.getId());
                latch.countDown();
            });
        }
        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        System.out.println("Unique IDs: " + uniqueIds);
        System.out.println("\n  Unique IDs created: " + uniqueIds.size());
        if (uniqueIds.size() > 1) {
            System.out.println("  ❌ BUG DETECTED! Multiple instances were created!");
            System.out.println("     (Race condition in getInstance())");
        } else {
            System.out.println("  ✅ Only one instance created (this time lucky!)");
        }
    }
}
