package lambda;

import java.util.ArrayList;
import java.util.function.Predicate;

public class LambdaEffectiveFinal {

    private String name;

    public static void main(String[] args) {

        ArrayList<String> al = new ArrayList<>();
        al.add("Hello");

        int x = 12;
        Predicate<String> lambda = s -> {
            new LambdaEffectiveFinal().name = "kennedy";
            System.out.println("x == " + x);
            return s.isEmpty() && x % 2 == 0;
        };

        System.out.println(al);

        new LambdaEffectiveFinal().name = "kennedy";

//        x++;
    }
}
