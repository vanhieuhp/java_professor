package dev.hieunv.processfile;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hieunv.domain.entity.Person;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.List;

public class JsonToDbProcessor {

    private static final int BATCH_SIZE = 1000;

    public static void main(String[] args) {
        File jsonFile = new File("person.json");

        JsonFactory factory = new JsonFactory();
        ObjectMapper mapper = new ObjectMapper();
        List<Person> batch = new ArrayList<>();

        try (JsonParser parser = factory.createParser(jsonFile)) {
            if (parser.nextToken() != JsonToken.START_ARRAY) {
                throw new IllegalStateException("Expected JSON array");
            }

            while (parser.nextToken() == JsonToken.START_OBJECT) {
                Person person = mapper.readValue(parser, Person.class);
                batch.add(person);

                if (batch.size() >= BATCH_SIZE) {
                    saveToDatabase(batch);
                    batch.clear();
                }
            }

            // Save any remaining records
            if (!batch.isEmpty()) {
                saveToDatabase(batch);

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveToDatabase(List<Person> people) throws Exception {
        // Replace with your DB details
        String url = "jdbc:mysql://localhost:3306/epayjsc?useSSL=false&serverTimezone=UTC";
        String username = "root";
        String password = "password";

        try (Connection conn = DriverManager.getConnection(url, username, password)) {
            conn.setAutoCommit(false);

            String sql = "INSERT INTO people (name, age) VALUES (?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                for (Person person : people) {
                    stmt.setString(1, person.getName());
                    stmt.setInt(2, person.getAge());
                    stmt.addBatch();
                }

                stmt.executeBatch();
                conn.commit();
            }
        }
    }
}
