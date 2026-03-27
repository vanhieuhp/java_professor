import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class BenchmarkThread {

    static void simulateCallBankApi() throws InterruptedException {
        Thread.sleep(100); // simulate API call 100ms latency

    }

    static void main() throws ExecutionException, InterruptedException {
        int soTask = 1000;

        // Test 1: Platform threads (pool of 50)
        Instant start1 = Instant.now();
        ExecutorService executor  = Executors.newFixedThreadPool(50);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < soTask; i++) {
            Future<?> future = executor .submit(() -> {
                try {
                    simulateCallBankApi();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });;

            futures.add(future);
        }
        for (Future<?> future : futures) {
            future.get();
        }
        // TODO: run 1000 tasks with Executors.newFixedThreadPool(50)
        // Hint: submit all tasks, store in List<Future<?>>, then call .get() on each
        Duration platformTime = Duration.between(start1, Instant.now());
        executor.shutdown();

        // Test 2: Virtual threads
        Instant start2 = Instant.now();
        ExecutorService executor2 = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<?>> futures2 = new ArrayList<>();
        for (int i = 0; i < soTask; i++) {
            Future<?> future = executor2.submit(() -> {
                try {
                    simulateCallBankApi();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            futures2.add(future);
        }
        for (Future<?> future : futures2) {
            future.get();
        }
        // TODO: run 1000 tasks with Executors.newVirtualThreadPerTaskExecutor()
        Duration virtualTime = Duration.between(start2, Instant.now());
        executor2.shutdown();
        System.out.printf("Platform threads (pool 50): %dms%n", platformTime.toMillis());
        System.out.printf("Virtual threads: %dms%n", virtualTime.toMillis());
    }
}
