package java_effective.avoid_finalize;

import java.io.IOException;

public class BankingService {

    public void recordPayment(String accountId, double amount) {
        // try-with-resources: resource is closed when block exits
        // — normally
        // — on exception
        // — on return
        // — on break/continue
        try (TransactionLogGood log = new TransactionLogGood("txn.log")) {
            log.log("PAYMENT: " + accountId + " $" + amount);
            // business logic here...
        } catch (IOException e) {
            // close() was already called before this catch runs.
            // File is safe, log is closed.
            System.err.println("Failed to record transaction: " + e.getMessage());
        }
        // ✅ At this point, log.close() has definitely run.
    }
}
