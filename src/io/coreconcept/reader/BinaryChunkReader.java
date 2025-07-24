package io.coreconcept.reader;

import java.io.FileInputStream;

public class BinaryChunkReader {

    public static void main(String[] args) {
        byte[] buffer = new byte[4096];

        try (FileInputStream fis = new FileInputStream("largefile.bin")) {
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                System.out.println("Read " + bytesRead + " bytes");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
