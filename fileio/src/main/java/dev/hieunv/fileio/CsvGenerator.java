package dev.hieunv.fileio;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class CsvGenerator {

    static void main() throws IOException {
        Path path = Path.of("big-data.csv");
        Random random = new Random();

        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            for (int id = 1; id <= 3_000_000; id++) {
                long amount = random.nextInt(1_000_000);
                writer.write(id + "," + amount);
                writer.newLine();
            }
        }

        System.out.println("Done. File size: " + Files.size(path) / (1024 * 1024) + " MB");
    }
}
