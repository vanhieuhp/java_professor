package streams.exercises;

import java.util.Arrays;
import java.util.List;

public class Question13 {

    public static void main(String[] args) {
        List<Integer> ls = Arrays.asList(11, 11, 22, 33, 33, 55, 66);

        boolean isElevenContaining = ls.stream().distinct()
                .anyMatch(i -> i == 11);
        System.out.println("Is 11 contained in the list? " + isElevenContaining);

        System.out.println(ls.stream()
                .distinct()
                .noneMatch(x -> x % 11 > 0));
    }
}
