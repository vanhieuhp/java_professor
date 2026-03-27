import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PaymentBatch {

    record Payment (String id, double amount) {}
    record Result (String id, boolean success, String error) {}

    // Simulate processing a sing payment (may throw exception)
    Result process(Payment p) throws Exception {
        Thread.sleep(50); // simulate I/O
        if (p.amount() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }
        return new Result(p.id(), true, null);
    }

    // TODO: implement this method
    List<Result> processAll(List<Payment> payments) {
        // use virtual threads to process concurrently
        // return a result for all payments - both successful and failed
        // DO NOT let one failure stop the entire batch
        List<Future<Result>> futures = new ArrayList<>();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        for (Payment p : payments) {
            Future<Result> result = executor.submit(() -> process(p));
            futures.add(result);
        }

        List<Result> results = new ArrayList<>();
        for (int i = 0; i < futures.size(); i++) {
            Payment p = payments.get(i);
            try {
                results.add(futures.get(i).get());
            } catch (ExecutionException e) {
                results.add(new Result(p.id(), false, e.getCause().getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                results.add(new Result(p.id(), false, "Interrupted"));
            }
        }
        executor.shutdown();
        return results;
    }
}
