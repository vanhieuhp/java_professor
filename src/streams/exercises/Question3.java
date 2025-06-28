package streams.exercises;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class Question3 {

    public static void main(String[] args) {
        Stream<List<String>> streamOfLists = Stream.of(
                Arrays.asList("a", "b"),
                Arrays.asList("d", "c"),
                Arrays.asList("a", "c"));

        streamOfLists
                .filter(list -> list.contains("c"))
                .peek(list -> System.out.println("\n" + list))
                .flatMap(list -> list.stream())
                .forEach(str -> System.out.print(str + " "));
        System.out.println();
    }
}
