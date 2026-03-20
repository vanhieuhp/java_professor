package multithread.codelab.deadlock;

import multithread.codelab.reentrantlock.Account;

public class DeadlockDemo {

    public static void main(String[] args) throws InterruptedException {
        Account a = new Account("A", 1000);
        Account b = new Account("B", 1000);

        TransferServiceDeadlock service = new TransferServiceDeadlock();
        Thread t1 = new Thread(() -> service.transfer(a, b, 100));
        Thread t2 = new Thread(() -> service.transfer(b, a, 100));
        System.out.println("Starting two transfers in opposite directions...");
        System.out.println("Watch: within 3s, both threads will be BLOCKED on each other's lock.");
        System.out.println("Run: jstack <pid> to see the deadlock.");
        System.out.println();

        t1.start();
        Thread.sleep(200);
        t2.start();
        t1.join(5000);
        t2.join(5000);

        System.out.println();
        System.out.println("After 5 seconds:");
        System.out.println("  Thread 1 state: " + t1.getState());   // BLOCKED
        System.out.println("  Thread 2 state: " + t2.getState());   // BLOCKED
        System.out.println();
        System.out.println("Run 'jstack' on this process to see:");
        System.out.println("  'Found one Java-level deadlock:'");
        System.out.println("  'waiting for monitor locks ...'");
        System.out.println("  'locked <0x...>' (twice per thread)");
    }
}
