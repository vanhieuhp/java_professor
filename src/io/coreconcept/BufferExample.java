package io.coreconcept;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class BufferExample {

    public static void main(String[] args) {

        try {
//            BufferedWriter bw = new BufferedWriter(new FileWriter("big.txt"));
//            for (int i = 0; i < 1_000_000; i++) {
//                bw.write("Line " + i);
//                bw.newLine();
//            }
//            bw.close();

            FileWriter fw = new FileWriter("big.txt");
            for (int i = 0; i < 1_000_000; i++) {
                fw.write("Line " + i);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
