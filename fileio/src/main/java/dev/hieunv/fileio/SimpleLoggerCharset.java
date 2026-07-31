package dev.hieunv.fileio;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

public class SimpleLoggerCharset {

    static void main() throws IOException {
        BufferedReader reader = Files.newBufferedReader(Paths.get("hello.txt"), StandardCharsets.UTF_8);
        try(reader) {
            System.out.println(reader.readLine());
        }
    }
}
