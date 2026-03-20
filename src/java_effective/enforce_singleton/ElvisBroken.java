package java_effective.enforce_singleton;

// ❌ Bad — Public static final field (not thread-safe without careful handling):
public class ElvisBroken {

    public static final ElvisBroken INSTANCE = new ElvisBroken();
    private ElvisBroken() {
        // Protect against reflection attack!
        if (INSTANCE != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    public void sing() {
        System.out.println("Elvis is singing");
    }
}
