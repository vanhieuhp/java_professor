package streams.exercises;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Question1 {

    public static void main(String[] args) {
        IntStream intStream1 = IntStream.range(0, 5);
        IntStream intStream2 = IntStream.rangeClosed(0, 5);

        intStream1.forEach(System.out::print);
        System.out.println();
        intStream2.forEach(System.out::print);

        System.out.println();
        IntStream intStream3 = IntStream.range(0, 5);
        double avg = intStream3.average().orElse(0);
        System.out.println("Average is " + avg);

        List<Person> people = Arrays.asList(
                new Person("Alan", "Burke", 20),
                new Person("Zoe", "Peters", 20),
                new Person("Peter", "Castle", 29)
        );
        Person oldestPerson = people.stream()
                .max(Comparator.comparing(p -> p.getAge()))
                .get();

        System.out.println("Oldest person is " + oldestPerson);

        List<Integer> numbers = Arrays.asList(10, 47, 33, 23);
        int maxValue1 = numbers.stream()
                .reduce(Integer.MIN_VALUE, (a, b) ->  Integer.max(a, b));
        System.out.println("Max value1 is " + maxValue1);

        int maxValue2 = numbers.stream()
                .reduce((a, b) -> Integer.max(a, b))
                .get();
        System.out.println("Max value2 is " + maxValue2);
    }


}
