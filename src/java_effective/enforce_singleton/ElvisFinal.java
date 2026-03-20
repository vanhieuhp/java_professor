package java_effective.enforce_singleton;

public class ElvisFinal {

    public static final ElvisFinal INSTANCE = new ElvisFinal();

    private ElvisFinal() {
        // Reflection protection
        if (INSTANCE != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    private Object readResolve() {
        return INSTANCE;
    }

    public void sing() {
        System.out.println("Elvis is singing");
    }
}
