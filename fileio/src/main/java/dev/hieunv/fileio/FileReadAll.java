package dev.hieunv.fileio;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class FileReadAll {
    static void main() throws IOException {
        Path path = Paths.get("big-data.csv");
        long start = System.currentTimeMillis();
        countAllLines(path);
        long end = System.currentTimeMillis();
        System.out.println("countAllLines Time: " + (end - start) + "ms");

        start = System.currentTimeMillis();
        countLinesStreaming(path);
        end = System.currentTimeMillis();
        System.out.println("countLinesStreaming Time: " + (end - start) + "ms");
    }

    public static void countAllLines(Path path) throws IOException {
        long sum = 0;
        List<String> lines  =  Files.readAllLines(path);
        for (String line : lines) {
            String[] values = line.split(",");
            sum += Long.parseLong(values[1]);
        }
        System.out.println("Sum: " + sum);
    }

    public static void countLinesStreaming(Path path) throws IOException {
        BufferedReader bufferedReader = Files.newBufferedReader(path);
        long sum = 0;
        for (String line; (line = bufferedReader.readLine()) != null; ) {
            String[] values = line.split(",");
            sum += Long.parseLong(values[1]);
        }
        System.out.println("Sum: " + sum);
    }
}
