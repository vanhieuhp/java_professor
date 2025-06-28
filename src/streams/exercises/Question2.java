package streams.exercises;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Question2 {

    public static void main(String[] args) {
        List<Item> items = new ArrayList<Item>();
        items.add(new Item(1, "Screw"));
        items.add(new Item(2, "Nail"));
        items.add(new Item(3, "Bolt"));

        items.stream().sorted(
                (Comparator.comparing(i -> i.getName()))
        ).forEach(System.out::println);


    }
}
