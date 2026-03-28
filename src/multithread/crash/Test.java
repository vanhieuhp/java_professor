package multithread.crash;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Test {
    public static void main(String[] args) throws ExecutionException, InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(4);
//        for (int i = 0; i < 100; i++) {
//            pool.submit(() -> {
//                System.out.println(Thread.currentThread().getName() + " is running");
//            });
//        }

        Future<Integer> future = pool.submit(() -> {
//            System.out.println(Thread.currentThread().getName() + " is running");
            Thread.sleep(2000);
            return 42;
        });

        // Main thread is free to do other work here!
        System.out.println("Doing other stuff while we wait...");

// Now we actually need the result
        Integer result = future.get();  // BLOCKS until the result is ready
        System.out.println("Answer: " + result);  // Answer: 42
    }
}
