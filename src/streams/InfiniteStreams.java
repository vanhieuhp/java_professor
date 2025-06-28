package streams;

import java.util.stream.Stream;

public class InfiniteStreams {

    public static void main(String[] args) {
//        for (int i = 1; i < 50; i++) {
//            System.out.println(rand());
//        }

//        iterate();
        iterateWithLimit();
    }

    public static int rand() {
        return (int) (Math.random() * 10);
    }

    public static void iterate() {
        // infinite stream of ordered numbers
//        iterate(T seed, UnaryOperator<T> fn)
        Stream<Integer> infStream = Stream.iterate(2, n -> n + 2);

        // keep going until I kill it.
        infStream.forEach(System.out::println);
    }

    public static void iterateWithLimit() {
        Stream
                .iterate(2, n -> n + 2)
                .limit(10)
                .forEach(System.out::println);
    }
}
