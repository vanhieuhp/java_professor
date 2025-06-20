package lambda;

import java.util.function.Predicate;

public class TestPredicate {

    @FunctionalInterface
    interface Evaluate<T> {
        boolean isNegative(T t);
    }

    public static void main(String[] args) {
        Evaluate<Integer> lambda = i -> i < 0;
        System.out.println(lambda.isNegative(10));
        System.out.println(lambda.isNegative(-10));

        int x = 4;
        System.out.println("Is " + x + " even? " + check(4, n -> n % 2 == 0));
        x = 7;
        System.out.println("Is " + x + " even? " + check(7, n -> n % 2 == 0));

        String name = "John";
        System.out.println("Does " + name + " start with J? " + check(name, n -> n.startsWith("Ja")));
        name = "Jane";
        System.out.println("Does " + name + " start with J? " + check(name, n -> n.startsWith("Ja")));
    }

    public static <T> boolean check(T t, Predicate<T> lambda) {
        return lambda.test(t);
    }

}

