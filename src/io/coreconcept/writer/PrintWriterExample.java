package io.coreconcept.writer;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class PrintWriterExample {

    public static void main(String[] args) {
        try (PrintWriter pw = new PrintWriter(new FileWriter("output.txt"), true)) {
            pw.println("Java IO is easy!");
            pw.printf("PI approx = %.2f%n", Math.PI);
            pw.println("Line 3");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
