package dev.hieunv.grpcserver.utils;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class DiskFileStorage {

    private final ByteArrayOutputStream byteArrayOutputStream;

    public DiskFileStorage() {
        this.byteArrayOutputStream = new ByteArrayOutputStream();
    }

    public ByteArrayOutputStream getByteArrayOutputStream() {
        return this.byteArrayOutputStream;
    }

    public void write(String fileNameWithType) throws IOException {
        Path dir = Paths.get("output");
        Files.createDirectories(dir); // safe even if already exists
        Path file = dir.resolve(fileNameWithType);
        try (OutputStream out = Files.newOutputStream(file)) {
            this.byteArrayOutputStream.writeTo(out);
        }
    }

    public void close() throws IOException{
        this.byteArrayOutputStream.close();
    }
}
