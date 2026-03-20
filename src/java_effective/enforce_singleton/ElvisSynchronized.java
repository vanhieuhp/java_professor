package java_effective.enforce_singleton;

// ❌ Bad — Synchronized method (simple but slow):
public class ElvisSynchronized {
    private static ElvisSynchronized instance;

    public static synchronized ElvisSynchronized getInstance() {
        if (instance == null) {
            instance = new ElvisSynchronized();
        }
        return instance;
    }
}
