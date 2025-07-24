package io.coreconcept;

import java.io.FileInputStream;
import java.io.IOException;

public class ByteInputExample {

    public static void main(String[] args) {
        try {
            FileInputStream input = new FileInputStream("Cats.txt");
            int data;
            while ((data = input.read()) != -1) {
                System.out.print(data);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
