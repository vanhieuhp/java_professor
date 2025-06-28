package streams;

import java.util.stream.Stream;

public class IntermediateOperation {

    public static void main(String[] args) {
        sorted1();
    }

    public static void sorted1() {
        Stream.of("Tim", "Jim", "Peter", "Ann", "Mary")
                .peek(name -> System.out.println(" 0." + name))
                .filter(name -> name.length() == 3)
                .peek(name -> System.out.println(" 1." + name))
                .sorted()
                .peek(name -> System.out.println(" 2." + name))
                .limit(2)
                .forEach(name -> System.out.println(" 3." + name));
    }
}
