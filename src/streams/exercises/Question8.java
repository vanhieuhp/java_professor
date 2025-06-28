package streams.exercises;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Question8 {

    public static void main(String[] args) {
        List<Book> books = Arrays.asList(
                new Book("Gone with the wind", 5.0),
                new Book("Gone with the wind", 10.0),
                new Book("Atlas shrugged", 15.0)
        );

        books.stream()
                .collect(
                        Collectors.toMap(
                                b -> b.getTitle(),
                                b -> b.getPrice(),
                                (v1,v2) -> v1*v2
                        )
                )
                .forEach((a, b) -> System.out.println(a + " " + b));
    }
}
