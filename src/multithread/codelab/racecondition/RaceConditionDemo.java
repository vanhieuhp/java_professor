package multithread.codelab.racecondition;

public class RaceConditionDemo {

    public static void main(String[] args) throws InterruptedException{
        int THREADS = 100;
        int DEBIT_PER_THREAD = 10;
        double INITIAL_BALANCE = 1000.0;
        double EXPECTED_FINAL = INITIAL_BALANCE - (THREADS * DEBIT_PER_THREAD);

        System.out.println("=== UNSAFE VERSION ===");
        System.out.printf("Initial balance: $%.2f | Threads: %d x debit($%d) | Expected: $%.2f%n",
                INITIAL_BALANCE, THREADS, DEBIT_PER_THREAD, EXPECTED_FINAL);

        for (int run = 0; run < 5; run++) {
            BankAccountUnsafe account = new BankAccountUnsafe(INITIAL_BALANCE);
            Thread[] threads = new Thread[THREADS];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < DEBIT_PER_THREAD; j++) {
                        account.debit(1.0);
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            System.out.printf("  Run %d: Balance = $%.2f  [%s]%n",
                    run,
                    account.getBalance(),
                    Math.abs(account.getBalance() - EXPECTED_FINAL) < 0.01 ? "CORRECT" : "WRONG");
        }

        System.out.println("%n=== SAFE VERSION (5 runs) ===");
        for (int run = 0; run < 5; run++) {
            BankAccountSafe account = new BankAccountSafe(INITIAL_BALANCE);
            Thread[] threads = new Thread[THREADS];

            for (int i = 0; i < threads.length; i++) {
                threads[i] = new Thread(() -> {
                    for (int j = 0; j < DEBIT_PER_THREAD; j++) {
                        account.debit(1.0);
                    }
                });
            }

            for (Thread thread : threads) {
                thread.start();
            }

            for (Thread thread : threads) {
                thread.join();
            }

            System.out.printf("  Run %d: Balance = $%.2f  [%s]%n",
                    run,
                    account.getBalance(),
                    Math.abs(account.getBalance() - EXPECTED_FINAL) < 0.01 ? "CORRECT" : "WRONG");
        }
    }
}
