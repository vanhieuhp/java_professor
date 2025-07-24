package io.coreconcept;

import java.io.FileOutputStream;

public class ByteOutputExample {

    public static void main(String[] args) {

        try (FileOutputStream output = new FileOutputStream("output.txt")) {
            String text = "Hello Java IO!";
            output.write(text.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
