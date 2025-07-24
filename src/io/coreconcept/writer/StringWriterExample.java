package io.coreconcept.writer;

import java.io.PrintWriter;
import java.io.StringWriter;

public class StringWriterExample {

    public static void main(String[] args) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        pw.println("Hello in memory!");
        pw.printf("User: %s, Age: %d%n", "John", 25);
        System.out.println(sw.toString());
    }
}
