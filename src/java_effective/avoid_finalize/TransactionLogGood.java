package java_effective.avoid_finalize;

import java.io.FileWriter;
import java.io.IOException;

public class TransactionLogGood implements AutoCloseable {
    private final String filename;
    private FileWriter writer;

    public TransactionLogGood(String filename) throws IOException {
        this.filename = filename;
        this.writer = new FileWriter(filename, true);  // open file handle
    }

    public void log(String message) throws IOException {
        writer.write(message + System.lineSeparator());
        writer.flush();  // explicitly flush on each important write
    }

    // The single close() method handles ALL cleanup.
    // No finalize(), no duplication, no confusion.
    @Override
    public void close() throws IOException {
        if (writer != null) {
            writer.close();  // FileWriter.close() also flushes
            writer = null;   // optional: mark as closed for safety
        }
    }
}
