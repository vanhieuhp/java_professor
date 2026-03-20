package java_effective.enforce_noninstatiability.good;

public final class StringUtils {
    // 👇 The idiom — everything below this comment is unreachable from outside
    private StringUtils() {
        throw new AssertionError(
                "StringUtils is a noninstantiable utility class. " +
                        "All methods are static. Do not attempt to instantiate."
        );
    }

    // ---------- Validation ----------

    /**
     * Returns true if the string is null, empty, or whitespace-only.
     *
     * @param str the string to check
     * @return true if blank, false otherwise
     */
    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }

    // ---------- Transformation ----------

    /**
     * Converts a camelCase string to SCREAMING_SNAKE_CASE.
     *
     * @param input camelCase string (e.g. "orderStatusCode")
     * @return SCREAMING_SNAKE_CASE (e.g. "ORDER_STATUS_CODE")
     */
    public static String toScreamingSnakeCase(String input) {
        if (input == null) return "";
        return input
                .replaceAll("([a-z])([A-Z])", "$1_$2")  // "orderStatus" → "order_Status"
                .replaceAll("\\s+", "_")                  // spaces → underscores
                .toUpperCase();                          // "order_Status" → "ORDER_STATUS"
    }

    // ---------- Truncation ----------

    /**
     * Truncates a string to maxLen characters, appending "..." if truncated.
     * Does nothing if string is shorter than maxLen.
     */
    public static String truncate(String input, int maxLen) {
        if (input == null) return "";
        if (maxLen < 3) throw new IllegalArgumentException("maxLen must be >= 3");
        if (input.length() <= maxLen) return input;
        return input.substring(0, maxLen - 3) + "...";
    }
}
