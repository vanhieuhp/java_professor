package io.coreconcept.reader;

import java.io.BufferedReader;
import java.io.FileReader;

public class ChunkedTextReader {

    public static void main(String[] args) {
        try (BufferedReader reader = new BufferedReader(new FileReader("large.txt"))) {
            String line;
            int count = 0;

            while((line = reader.readLine()) != null) {
                System.out.println("line " + ++count + ": " + line + "");

                // simulate processing per chunk
                if(count % 1000 == 0) {
                    System.out.println("processed " + count + " lines");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
