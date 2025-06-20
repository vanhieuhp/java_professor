package lambda;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.*;

public class FunctionInterfaceFromAPI {

    public static void main(String[] args) {
        FunctionInterfaceFromAPI fiAPI = new FunctionInterfaceFromAPI();
//        fiAPI.predicate(); // boolearn
//        fiAPI.supplier(); // object
//        fiAPI.consumer(); // void
//        fiAPI.function(); // object
//        fiAPI.unaryBinaryOperator(); // object

    }

    public void predicate() {
        Predicate<String> pStr = s -> s.contains("city");
        System.out.println(pStr.test("New York City"));
        System.out.println(pStr.test("New York city"));

        BiPredicate<String, Integer> checkLength = (str, len) -> str.length() == len;
        System.out.println(checkLength.test("Vatican City", 10));
    }

    public void supplier() {
        Supplier<StringBuilder> supStringBuilder = () -> new StringBuilder();
        System.out.println("Supplier StringBuilder: " + supStringBuilder.get().append("Hello World!"));

        Supplier<LocalTime> supLocalTime = () -> LocalTime.now();
        System.out.println("Supplier Time: " + supLocalTime.get());

        Supplier<Double> supDouble = () -> Math.random();
        System.out.println("Supplier Double: " + supDouble.get());
    }

    public void consumer() {
        Consumer<String> printC = s -> System.out.println(s); // lambda
        printC.accept("To be or not to be, that is the question.");

        List<String> names = new ArrayList<>();
        names.add("John");
        names.add("Jane");
        names.forEach(printC);

        var mapCapitalCities = new HashMap<String, String>();
        BiConsumer<String, String> biCon = (key, value) -> mapCapitalCities.put(key, value);
        biCon.accept("Dublin", "Ireland");
        biCon.accept("Washington D.C.", "USA");
        System.out.println(mapCapitalCities);

        BiConsumer<String, String> mapPrint = (key, value) -> System.out.println(key + ": " + value);
        mapCapitalCities.forEach(mapPrint);
    }

    public void function() {
        Function<String, Integer> fn2 = s -> s.length();
        System.out.println("Function: " + fn2.apply("Hello World!"));

        BiFunction<String, String, Integer> biFn = (s1, s2) -> s1.length() + s2.length();
        System.out.println("BiFunction: " + biFn.apply("Hello", "World!"));

        BiFunction<String, String, String> biFn2 = (s1, s2) -> s1.concat(s2);
        System.out.println("BiFunction: " + biFn2.apply("Hello", "World!"));
    }

    public void unaryBinaryOperator() {
        UnaryOperator<String> unaryOp = name -> "My name is " + name;
        System.out.println("UnaryOperator: " + unaryOp.apply("John"));

        BinaryOperator<String> binaryOp = (n1, n2) -> n1.concat(n2);
        System.out.println("BinaryOperator: " + binaryOp.apply("John", "Doe"));
    }

}
