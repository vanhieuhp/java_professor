package streams.exercises;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

public class Question4 {
    public static void main(String[] args) {

        List<Integer> listInt = Arrays.asList(1, 2, 3);
        int sum = listInt.stream()
                .mapToInt(i -> i)
                .sum();
        System.out.println("sum == "+sum);

        int max = listInt.stream()
                .mapToInt(i -> i.intValue())
                .max()
                .getAsInt();
        System.out.println("max == "+max);
    }
}
