package streams.exercises;

import java.util.Optional;

public class Question5 {

    public static void main(String[] args) {
        Optional<String> grade1 = getGrade(50);
        Optional<String> grade2 = getGrade(55);

        System.out.println(grade1.orElse("UNKNOWN"));
        if (grade2.isPresent()) {
            grade2.ifPresent(System.out::println);
        } else {
            System.out.println(grade2.orElse("Empty"));
        }
    }

    public static Optional<String> getGrade(int marks) {
        Optional<String> grade = Optional.empty();
        if (marks >= 50) {
            grade = Optional.of("PASS");
        } else {
            grade = Optional.of("FAIL");
        }

        return grade;
    }
}
