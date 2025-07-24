package dev.hieunv.domain.entity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SequenceWriter;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PersonGenerator {

    private static final String[] cities = {"Hanoi", "Saigon", "Da Nang", "Hue", "Can Tho"};
    private static final String[] countries = {"Vietnam", "USA", "Canada", "UK", "Germany"};
    private static final String[] jobs = {"Developer", "Manager", "Analyst", "Tester", "Architect"};

    public static void main(String[] args) {
        ObjectMapper mapper = new ObjectMapper();
        File outputfile = new File("person.json");
        Random random = new Random();
        List<Person> buffer = new ArrayList<>();
        int batchSize = 10000;

        try (SequenceWriter writer = mapper.writer().writeValuesAsArray(outputfile)) {
            for (int i = 0; i < 1_000_000; i++) {
                Person p = new Person(
                        i,
                        "User_" + i,
                        random.nextInt(50) + 20,
                        "user" + i + "@example.com",
                        "09" + (random.nextInt(90000000) + 10000000),
                        "123 Street #" + i,
                        cities[random.nextInt(cities.length)],
                        countries[random.nextInt(countries.length)],
                        jobs[random.nextInt(jobs.length)],
                        random.nextDouble() * 100_000
                );
                buffer.add(p);
                if (i % batchSize == 0) {
                    writer.writeAll(buffer);
                    buffer.clear();
                    System.out.println("Written up to: " + i);
                }
            }

            if (!buffer.isEmpty()) {
                writer.writeAll(buffer);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
