package streams.exercises;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class Question14 {

    public static void main(String[] args) {
        AtomicInteger ai = new AtomicInteger();
        Stream.of(11, 11, 22, 33)
                .parallel()
                .filter(n -> {
                            ai.incrementAndGet();
                            return n % 2 == 0;
                        }
                )
                .forEach(System.out::println);
        System.out.println("ai.get() == " + ai.get());

        Stream<Integer> stream = Stream.of(1, 2, 3, 4, 5);
        Stream<Integer> stream2 =  stream.filter(e -> {
            ai.incrementAndGet();
            return e % 2 == 0;
        });
        stream2.forEach(System.out::println);
        System.out.println("ai.get() == " + ai.get());
    }
}
