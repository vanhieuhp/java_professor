package streams.exercises;

import java.util.stream.DoubleStream;

public class Question12 {

    public static void main(String[] args) {
        DoubleStream doubleStream = DoubleStream.of(0, 2, 4);
        double sumEven =  doubleStream
                .filter(i -> i % 2 != 0)
                .sum();
        System.out.println("Sum of even numbers is " + sumEven);
    }
}
