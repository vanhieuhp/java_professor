package streams.exercises;

import java.util.Arrays;
import java.util.List;

public class Question9 {

    public static void main(String[] args) {
        List<Person> people = Arrays.asList(
                new Person("Bob", 31),
                new Person("Paul", 32),
                new Person("John", 33)
        );

        double avgAge = people
                .stream()
                .filter(s -> s.getAge() < 30)
                .mapToInt(s -> s.getAge())
                .average()
                .orElse(0.0);
        System.out.println("Average age is " + avgAge);
    }
}
