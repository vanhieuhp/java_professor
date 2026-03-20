package multithread.codelab.deadlock;

import multithread.codelab.reentrantlock.Account;

// ❌ DEADLOCK-PRONE: lock order depends on call site
public class TransferServiceDeadlock {

    public void transfer(Account from, Account to, double amount) {
        synchronized (from) {           // Thread A: locks from first
            synchronized (to) {
                if (from.debit(amount)) {
                    to.credit(amount);
                    System.out.printf("Transferred $%.2f: %s -> %s%n",
                            amount, from.getId(), to.getId());
                }
            }
        }
    }
}
