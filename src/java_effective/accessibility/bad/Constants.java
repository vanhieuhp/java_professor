package java_effective.accessibility.bad;

public class Constants {
    // DANGER: This array is mutable!
    // Anyone with access to the reference can modify its contents.
    // Even though 'VALUES' is final, the ARRAY CONTENTS are NOT.
    public static final String[]  VALUES = {"ERROR", "WARNING", "INFO"};

    public static void main(String[] args) {
        // Changing internal state without touching Constants class
        Constants.VALUES[0] = "OVERRIDDEN";  // Internal state mutated!
        // This "constant" now returns something completely different
        System.out.println(Constants.VALUES[0]); // "OVERRIDDEN" — not a constant!
    }
}
