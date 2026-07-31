package dev.hieunv.fileio;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class ReadCsv {
    static void main() throws IOException {
        String filename = "sample.csv";
        BufferedReader reader = Files.newBufferedReader(Path.of(filename));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .build();

        try (CSVParser parser = CSVParser.parse(reader, format)) {
            for (var record : parser) {
                System.out.println(record.get("id") + " | " + record.get("name") + " | " + record.get("city"));
            }
        }
    }
}
