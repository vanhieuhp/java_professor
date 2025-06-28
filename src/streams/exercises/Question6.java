package streams.exercises;

import java.util.Arrays;
import java.util.List;

public class Question6 {

    public static void main(String[] args) {
//        a. title=”Thinking in Java”, price=30.0
//        b. title=”Java in 24 hrs”, price=20.0
//        c. title=”Java Recipes”, price=10.0
        List<Book> books = Arrays.asList(
                new Book("Thinking in Java", 30.0),
                new Book("Java in 24 hrs", 20.0),
                new Book("Java Recipes", 10.0)
        );
        double avgPrice = books.stream()
                .mapToDouble(b -> b.getPrice())
                .filter(p -> p > 10)
                .average()
                .getAsDouble();
        System.out.println("Average price is " + avgPrice);

        List<Book> bookList = books.stream()
                .filter(b -> b.getPrice() > 90)
                .toList();
        System.out.println(bookList);
    }
}
