package java_effective.enforce_noninstatiability.bad;

// PasswordChecker.java
// No constructor declared — Java silently adds:
// public PasswordChecker() {}   ← public! anyone can call it
public class PasswordChecker {

    // Only static utility methods — no instance state needed
    public static boolean isStrong(String password) {
        return password != null
                && password.length() >= 8
                && password.matches(".*[A-Z].*")
                && password.matches(".*\\d.*");
    }

    public static void main(String[] args) {
        // Any developer (or test) can accidentally instantiate this:
        PasswordChecker checker = new PasswordChecker(); // ← Compiles! Runs! Returns a useless object
        checker.isStrong("Pass1234"); // Works, but the instance is meaningless noise in the heap
    }
}

