package java_effective.enforce_singleton.exercise;

public class PaymentProcessor {
    private static PaymentProcessor instance;
    private int transactionCount = 0;

    private PaymentProcessor() {}

    public static PaymentProcessor getInstance() {
        if (instance == null) {  // First check
            synchronized (PaymentProcessor.class) {
                instance = new PaymentProcessor();
            }
        }
        return instance;
    }

    public void processPayment(double amount) {
        transactionCount++;
        System.out.println("Processing: $" + amount);
    }

    /*
    * What you need to do: Identify the thread-safety bug and fix it properly

Expected outcome: Always returns the same instance, even with 1000 threads calling simultaneously

Hint: What's missing in the synchronized block?
    * */

    enum PaymentProcessorEnum {
        INSTANCE;

        private int transactionCount = 0;
        public synchronized void processPayment(double amount) {
            transactionCount++;
            System.out.println("Processing: $" + amount + " | Total transactions: " + transactionCount);
        }
    }
}
