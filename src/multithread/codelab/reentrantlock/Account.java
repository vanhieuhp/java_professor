package multithread.codelab.reentrantlock;

import java.util.concurrent.locks.ReentrantLock;

public class Account {
    private final String id;
    private double balance;
    public final ReentrantLock lock = new ReentrantLock(); // dedicated lock object

    public Account(String id, double balance) {
        this.id = id;
        this.balance = balance;
    }

    public boolean debit(double amount) {
        if (balance >= amount) {
            balance -= amount;
            return true;
        }
        return false;
    }

    public void credit(double amount) {
        balance += amount;
    }

    public String getId() {
        return id;
    }

    public double getBalance() {
        return balance;
    }
}
