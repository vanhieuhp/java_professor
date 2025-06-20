package lambda;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class MethodReferenceTypes {
    public static void main(String[] args) {
        staticMethodReferences();
    }
    public static void staticMethodReferences() {
        Consumer<List<Integer>> sortList = list -> Collections.sort(list);
        Consumer<List<Integer>> sortMR = Collections::sort;

        List<Integer> listOfNumbers = Arrays.asList(2, 1, 5, 4, 9);
        sortList.accept(listOfNumbers);
        System.out.println(listOfNumbers);

        listOfNumbers = Arrays.asList(2, 1, 5, 4, 9);
        sortMR.accept(listOfNumbers);
        System.out.println(listOfNumbers);
    }
}
