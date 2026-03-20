package java_effective.accessibility.good;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class Constants {
    // ✅ List is immutable — add/remove/clear all throw UnsupportedOperationException
    public static final List<String> VALUES = List.of(
            "ERROR", "WARNING", "INFO"
    );

    public static final java.util.Set<String> LOG_LEVELS = Set.of("DEBUG", "INFO");
    public static final java.util.Map<String, Integer> HTTP_CODES = Map.of(
            "OK", 200, "NOT_FOUND", 404
    );

    public static void main(String[] args) {
        // This now throws UnsupportedOperationException
        Constants.VALUES.set(0, "OVERRIDDEN"); // Blocked! 💪

        // Also protects against subclass modification
        // Collections.unmodifiableList returns a wrapper that
        // throws on any mutative operation
    }
}
