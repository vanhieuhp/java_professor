package io.coreconcept;

import java.io.File;

public class FileExample {
    public static void main(String[] args) {
        File file = new File("example.txt");

        System.out.println("Exists: " + file.exists());
        System.out.println("Path: " + file.getAbsolutePath());
        System.out.println("Is File: " + file.isFile());
        System.out.println("Is Directory: " + file.isDirectory());
    }
}
