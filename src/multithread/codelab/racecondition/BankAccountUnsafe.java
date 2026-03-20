package multithread.codelab.racecondition;

public class BankAccountUnsafe {

    private double balance;

    public BankAccountUnsafe(double balance) {
        this.balance = balance;
    }

    public void debit(double amount) {
        if (balance >= amount) {
            // artificial delay amplifies the race window
            // (makes the bug visible even in low-contention scenarios)
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
            }
            balance = balance - amount;
        }
    }

    public double getBalance() {
        return balance;
    }
}
