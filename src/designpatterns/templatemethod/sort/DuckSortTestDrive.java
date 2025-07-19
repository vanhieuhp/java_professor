package designpatterns.templatemethod.sort;

import java.util.Arrays;

public class DuckSortTestDrive {

    public static void main(String[] args) {
        Duck[] ducks = {
                new Duck("Daffy", 8),
                new Duck("Dewey", 2),
                new Duck("Howard", 4),
                new Duck("Louie", 6),
                new Duck("Donald", 10),
                new Duck("Huey", 1),
                new Duck("Dewey", 2),
                new Duck("Louie", 6),
                new Duck("Donald", 10),
        };
        System.out.println("Before sorting: ");
        for (Duck duck : ducks) {
            System.out.println(duck);
        }

        Arrays.sort(ducks);

        System.out.println("\nAfter sorting: ");
        for (Duck duck : ducks) {
            System.out.println(duck);
        }
    }
}
