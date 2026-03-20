package java_effective.enforce_singleton;

// ✅ Good — Enum Singleton (THE recommended approach):
public enum Elvis {

    INSTANCE;

    public void sing() {
        System.out.println("Elvis is singing");
    }

    // you can add methods and files
    public String getNamer() {
        return "Elvis";
    }
}
