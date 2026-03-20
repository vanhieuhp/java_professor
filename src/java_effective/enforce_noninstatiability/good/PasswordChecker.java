package java_effective.enforce_noninstatiability.good;

// Noninstantiability enforced — cannot be subclassed or instantiated
public class PasswordChecker {
    // ✅ Private constructor: blocks all public access
    // ✅ throw AssertionError: guards even against reflective calls
    // ✅ no-arg constructor: technically allowed, but unreachable from normal code
    private PasswordChecker() {
        throw new AssertionError("Instantiation of utility class 'PasswordChecker' is not permitted");
    }

    // All static methods — no instance state
    public static boolean isStrong(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*");
    }

    public static boolean hasCommonPatterns(String password) {
        String[] banned = {"password", "12345678", "qwerty"};
        for (String bannedPw : banned) {
            if (password.equalsIgnoreCase(bannedPw)) return true;
        }
        return false;
    }
}
