package multithread.codelab.racecondition;

public class BankAccountSafe {
    private double balance;

    public BankAccountSafe(double initial) {
        this.balance = initial;
    }

    // SAFE: synchronized gates the ENTIRE read-check-write sequence
    public synchronized boolean debit(double amount) {
        if (balance < amount) return false;
        try {
            Thread.sleep(1);
        } catch (InterruptedException ignored) {
        }
        balance -= amount;
        return true;
    }

    public synchronized double getBalance() {
        return balance;
    }
}
