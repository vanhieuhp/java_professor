package java_effective.eliminate_obsolete.bad;

import java.util.ArrayList;
import java.util.List;

public class PoolDemo {
    public static void main(String[] args) {
        ConnectionPoolBad pool = new ConnectionPoolBad(3);

        // Acquire and release repeatedly
        for (int cycle = 0; cycle < 3; cycle++) {
            List<byte[]> held = new ArrayList<>();

            // Acquire all available connections
            for (int i = 0; i < 3; i++) {
                held.add(pool.acquire());
            }

            // Release them all
            for (byte[] conn : held) {
                pool.release(conn);  // returned to pool
            }
        }

        // Pool should have 3 connections available
        // But does it? What's actually in the list?
        System.out.println("Available: " + pool.availableCount());  // expected: 3

        // The real test: hold 3 more, release 3 more — pool should NOT exceed maxSize
        List<byte[]> more = new ArrayList<>();
        for (int i = 0; i < 3; i++) more.add(pool.acquire());
        for (byte[] conn : more) pool.release(conn);

        System.out.println("Available after overflow: " + pool.availableCount());
        // What does this print? Why?
    }
}
