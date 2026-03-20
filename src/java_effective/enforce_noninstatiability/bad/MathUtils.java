package java_effective.enforce_noninstatiability.bad;

public abstract class MathUtils {
    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    public static void main(String[] args) {
        // This does NOT prevent instantiation:
        MathUtils u = new MathUtils() {}; // Anonymous subclass — compiles and runs fine
    }
}
