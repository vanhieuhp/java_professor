package streams;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CollectorsExamples {

    public static void main(String[] args) {
//        doAveragingInt();
//        doJoining();
//        doCollectToMap1();
//        doCollectToMap2();
//        doCollectToMap3();
//        doGroupingBy1();
//        doGroupingBy2();
//        doGroupingBy3();
        doPartitioning1();
        doPartitioning2();
        doPartitioning3();
        doPartitioning4();
    }

    public static void doPartitioning4() {
        Stream<String> names = Stream.of("Alan", "Teresa", "Mike", "Alan", "Peter");
        Map<Boolean, Set<String>> map =
                names.collect(
                        Collectors.partitioningBy(
                                s -> s.length() > 4,
                                Collectors.toSet()
                        )
                );
        System.out.println(map);
    }

    public static void doPartitioning3() {
        Stream<String> names = Stream.of("Thomas", "Teresa", "Mike", "Alan", "Peter");
        Map<Boolean, List<String>> map =
                names.collect(
                        Collectors.partitioningBy(s -> s.length() > 10)
                );
        System.out.println(map);
    }

    public static void doPartitioning2() {
        Stream<String> names = Stream.of("Thomas", "Teresa", "Mike", "Alan", "Peter");
        Map<Boolean, List<String>> map =
                names.collect(
                        Collectors.partitioningBy(s -> s.startsWith("T"))
                );
        System.out.println(map);
    }

    public static void doPartitioning1() {
        Stream<String> names = Stream.of("Thomas", "Teresa", "Mike", "Alan", "Peter");
        Map<Boolean, List<String>> map =
                names.collect(
                        Collectors.partitioningBy(
                                s -> s.length() > 4
                        )
                );
        System.out.println(map);
    }

    public static void doGroupingBy3() {
        Stream<String> names = Stream.of("Martin", "Peter", "Joe", "Tom", "Tom", "Ann", "Alan");
        Map<Integer, List<String>> map =
                names.collect(
                        Collectors.groupingBy(
                                String::length,
                                TreeMap::new,
                                Collectors.toList()
                        )
                );
        System.out.println(map);
        System.out.println(map.getClass());
    }

    public static void doGroupingBy2() {
        Stream<String> names = Stream.of("Martin", "Peter", "Joe", "Tom", "Tom", "Ann", "Alan");
        Map<Integer, Set<String>> map =
                names.collect(Collectors.groupingBy(
                        String::length,
                        Collectors.toSet()
                ));
        System.out.println(map);
    }

    public static void doGroupingBy1() {
        Stream<String> names = Stream.of("Martin", "Peter", "Joe", "Tom", "Tom", "Ann", "Alan");
        Map<Integer, List<String>> map =
                names.collect(
                        Collectors.groupingBy(String::length)
                );
        System.out.println(map);
    }

    public static void doCollectToMap3() {
        TreeMap<String, Integer> map =
                Stream.of("cake", "biscuits", "apple tart", "cake")
                        .collect(
                                Collectors.toMap(s -> s,
                                        s -> s.length(),
                                        (len1, len2) -> len1 + len2,
                                        () -> new TreeMap<>())
                        );
        System.out.println(map);
        System.out.println(map.getClass());
    }

    public static void doCollectToMap2() {
        Map<Integer, String> map = Stream.of("cake", "biscuits", "tart", "apple")
                .collect(
                        Collectors.toMap(s -> s.length(),
                                s -> s,
                                (s1, s2) -> s1 + ", " + s2)
                );
        System.out.println(map);
    }

    public static void doCollectToMap1() {
        Map<String, Integer> map = Stream.of("cake", "biscuits", "apple tart")
                .collect(
                        Collectors.toMap(s -> s,
                                s -> s.length())
                );
        System.out.println(map);
    }

    public static void doJoining() {
        String s = Stream.of("cake", "biscuits", "apple tart")
                .collect(Collectors.joining(", "));
        System.out.println(s);
    }

    public static void doAveragingInt() {
        Double avg = Stream.of("cake", "biscuits", "apple tart")
                .collect(Collectors.averagingInt(s -> s.length()));
        System.out.println(avg);
    }
}
