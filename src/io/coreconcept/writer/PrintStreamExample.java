package io.coreconcept.writer;

import java.io.FileOutputStream;
import java.io.PrintStream;

public class PrintStreamExample {

    public static void main(String[] args) {
        try (PrintStream ps = new PrintStream(new FileOutputStream("log.txt"))) {
            ps.println("Logging started...");
            ps.printf("current time: %tc%n", System.currentTimeMillis());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
