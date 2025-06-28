package streams.exercises;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class Question7 {

    public static void main(String[] args) {
//        a. title=”Atlas Shrugged”, price=10.0
//        b. title=”Freedom at Midnight”, price=5.0
//        c. title=”Gone with the wind”, price=5.0

        List<Book> books = Arrays.asList(
                new Book("Atlas Shrugged", 10.0),
                new Book("Freedom at Midnight", 5.0),
                new Book("Gone with the wind", 5.0)
        );

        Map<String, Double> bookMap = books.stream()
                .collect(
                        Collectors.toMap(
                                b -> b.getTitle(),
                                b -> b.getPrice()
                        )
                );

        BiConsumer<String, Double> funcBc = (a, b) -> {
            if (a.startsWith("A")) {
                System.out.println(b);
            }
        };
        bookMap.forEach(funcBc);
    }
}
