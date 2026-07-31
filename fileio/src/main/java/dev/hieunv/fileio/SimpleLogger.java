package dev.hieunv.fileio;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class SimpleLogger {

    static void main() throws IOException {
        BufferedWriter writer = new BufferedWriter(new FileWriter("app.log", true));
        try(writer) {
            for (int i = 0; i < 5; i++) {
                writer.write("Log line " + i);
                writer.newLine();
                writer.flush();
                if (i == 3) throw new RuntimeException("crash simulation!");
            }
        } catch (IOException | RuntimeException e) {
            e.printStackTrace();
        }
    }
}
