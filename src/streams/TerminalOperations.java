package streams;

import java.util.function.BinaryOperator;
import java.util.stream.Stream;

public class TerminalOperations {

    public static void main(String[] args) {
//        doCollect1();
//        deReducde3();
//        deReducde2();
        deReducde1();
    }

    public static void doCollect1() {
        StringBuilder word = Stream.of("ad", "jud", "i", "cate")
                .collect(() -> new StringBuilder(),
                        (sb, str) -> sb.append(str),
                        (sb1, sb2) -> sb1.append(sb2));

        System.out.println(word);
    }

    public static void deReducde3() {
        Stream<String> stream = Stream.of("car", "bus", "train", "aeroplane");
        int length = stream.reduce(0,
                (n, str) -> n + str.length(),
                (n1, n2) -> n1 + n2);
        System.out.println(length);
    }

    public static void deReducde2() {
        BinaryOperator<Integer> op = (a, b) -> a + b;
        Stream<Integer> empty = Stream.empty();
        Stream<Integer> oneElement = Stream.of(6);
        Stream<Integer> multipleElements = Stream.of(3, 4, 5);
        empty.reduce(op).ifPresent(System.out::println);
        oneElement.reduce(op).ifPresent(System.out::println);
        multipleElements.reduce(op).ifPresent(System.out::println);
        Integer val = Stream.of(1,1,1)
                .reduce(1, (a, b) -> a);
        System.out.println(val);
    }

    public static void deReducde1() {
        String name = Stream.of("s", "e", "a", "n")
                .reduce("", (s, c) -> s + c);
        System.out.println(name);

        Integer product = Stream.of(2, 3, 4)
                .reduce(1, (a, b) -> a * b);
        System.out.println(product);
    }
}
