package java_effective.enforce_singleton;

// ❌ Bad — Bill Pugh Singleton (inner class holder) — easily broken by serialization:
public class ElvisHolder {

    private ElvisHolder() {}
    private static class Holder {
        private static final ElvisHolder INSTANCE = new ElvisHolder();
    }
    public static ElvisHolder getInstance() {
        return Holder.INSTANCE;
    }
    // Problem: without readResolve(), deserialization creates NEW instance!
}
