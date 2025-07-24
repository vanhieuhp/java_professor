package io.coreconcept;

import java.io.FileWriter;

public class CharWriteExample {

    public static void main(String[] args) {
        try (FileWriter writer = new FileWriter("output.txt")) {
            writer.write("Java IO is easy!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
