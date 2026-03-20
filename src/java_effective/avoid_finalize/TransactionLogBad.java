package java_effective.avoid_finalize;

import java.io.FileWriter;
import java.io.IOException;

public class TransactionLogBad implements AutoCloseable {

    private final String filename;
    private FileWriter writer;

    public TransactionLogBad(String filename) throws IOException {
        this.filename = filename;
        this.writer = new FileWriter(filename, true);  // open file handle
    }

    public void log(String message) throws IOException {
        writer.write(message + System.lineSeparator());
    }

    @Override
    protected void finalize() throws Throwable {
        // ❌ PROBLEM 1: No guarantee this ever runs.
        // JVM may shut down without calling finalize().
        // ❌ PROBLEM 2: If close() throws, exception is silently swallowed.
        // The file handle may leak.
        // ❌ PROBLEM 3: Performance cost — finalizable objects run ~50x slower.
        try {
            if (writer != null) {
                writer.close();  // what if this throws?
            }
        } finally {
            super.finalize();  // what if THIS throws?
        }
    }

    @Override
    public void close() throws IOException {
        // ❌ PROBLEM 4: if someone calls close() AND the GC calls finalize(),
        // you may get double-close bugs or race conditions.
        if (writer != null) {
            writer.close();
        }
    }
}
