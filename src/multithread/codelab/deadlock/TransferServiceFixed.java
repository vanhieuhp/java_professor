package multithread.codelab.deadlock;

import multithread.codelab.reentrantlock.Account;

public class TransferServiceFixed {

    public void Transfer(Account from, Account to, double amount) {
        Account first = from.getId().compareTo(to.getId()) < 0 ? from : to;
        Account second = from.getId().compareTo(to.getId()) < 0 ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (first.debit(amount)) { // careful: need to check on 'first' balance
                    // We'll use direct balance manipulation for clarity
                    double fbal = getBalance(first);
                    if (fbal >= amount) {
                        debitOnAccount(first, amount);
                        creditOnAccount(second, amount);
                        System.out.printf("Transferred $%.2f: %s -> %s%n",
                                amount, from.getId(), to.getId());
                    }
                }
            }
        }
    }

    private double getBalance(Account a) {
        return a.getBalance();
    }

    private void debitOnAccount(Account a, double amt) { /* simplified */ }

    private void creditOnAccount(Account a, double amt) { /* simplified */ }
}
