package streams;

import java.util.Arrays;
import java.util.stream.Stream;

public class BuildStreams {

    public static void main(String[] args) {
        String[] cities = {"Dublin", "London", "Paris", "New York", "Tokyo"};
        Stream<String> citiesStream = Arrays.stream(cities);
        System.out.println(citiesStream.count());

        // or we can use Stream.of()
        citiesStream = Stream.of(cities);
        System.out.println(citiesStream.count());

        Stream<Integer> numbers = Stream.of(1, 2, 3, 4, 5);
        System.out.println(numbers.count());
    }
}
