package java_effective.enforce_singleton;

// ❌ Bad — Double-checked locking (error-prone, easy to get wrong):
public class ElvisDCL {

    private static volatile ElvisDCL instance = new ElvisDCL();

    private ElvisDCL() {}

    public static ElvisDCL getInstance() {
        if (instance == null) {
            synchronized (ElvisDCL.class) {
                if (instance == null) {
                    instance = new ElvisDCL();
                }
            }
        }

        return instance;
    }


}
