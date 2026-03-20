package java_effective.avoid_creating_unnecessary_object.bad;

public class NewString {

    public String greatBad(String name) {
        return new String("Hello " + name);   // new String[] + new String allocation
    }

    // ❌ BAD — autoboxing in a loop creates MILLIONS of Long objects
// Each i (int) is boxed into a new Long every iteration.
// Long sum = 0L starts as a Long(0). Then sum += i boxes i into a NEW Long each time.
    public static void main(String[] args) {
        long start = System.nanoTime();

        Long sum = 0L;          // boxed Long, not primitive long
        for (int i = 0; i < 1_000_000; i++) {
            sum += i;            // each += boxes int i → new Long(i)
            // also creates new Long(sum) for the assignment
        }

        long elapsed = System.nanoTime() - start;
        System.out.println("Took: " + elapsed / 1_000_000 + " ms");
        // On some JVMs: ~800ms with Long, ~20ms with long
    }

    // ❌ BAD — compiles the regex EVERY iteration
// String.matches() creates a new Pattern, a new Matcher, and a new CharSequence
// for every single call — even if called 10,000 times with the same pattern.
    public static boolean hasValidEmailsBad(String input) {
        String[] lines = input.split("\n");
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";

        for (String line : lines) {
            if (line.trim().matches(emailRegex)) {  // Pattern.compile() called EACH iteration
                return true;
            }
        }
        return false;
    }

    // ❌ BAD — these constructors are deprecated (and rarely needed)
// Boolean.TRUE and Boolean.FALSE already exist in the Boolean class.
    public Boolean toBooleanBad(String value) {
        return new Boolean(value);  // creates new Boolean object — unnecessary
    }
}
