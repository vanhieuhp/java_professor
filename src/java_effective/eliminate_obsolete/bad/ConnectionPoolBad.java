package java_effective.eliminate_obsolete.bad;

import java.util.ArrayList;
import java.util.List;

public class ConnectionPoolBad {
    private final List<byte[]> available = new ArrayList<>();
    private final int maxSize;

    public ConnectionPoolBad(int maxSize) {
        this.maxSize = maxSize;
    }

    // Simulate acquiring a connection (just allocates a byte array)
    public byte[] acquire() {
        if (!available.isEmpty()) {
            // Return the last available connection
            return available.remove(available.size() - 1);
        }
        // Create a new "connection"
        return new byte[8192];  // simulate 8KB connection object
    }

    // Return a connection to the pool
    public void release(byte[] connection) {
        if (available.size() < maxSize) {
            available.add(connection);  // add back to available pool
        }
        // If pool is full, connection is dropped — but was it ever nulled?
    }

    public void releaseGood(byte[] connection) {
        if (connection == null) return;

        // Bug 2 fix: reject duplicates — identity check with ==
        if (available.contains(connection)) {
            throw new IllegalStateException(
                    "Connection already returned to pool — double-release detected!");
        }

        if (available.size() < maxSize) {
            available.add(connection);
        } else {
            // Bug 1 fix: be explicit when dropping — in a real pool, close() here
            // For byte arrays, just let it be GC'd, but log it
            System.out.println("Pool full — connection discarded (would close in production)");
        }
    }

    public int availableCount() {
        return available.size();
    }
}
