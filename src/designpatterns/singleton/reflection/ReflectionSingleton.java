package designpatterns.singleton.reflection;

public class ReflectionSingleton {

    private static final ReflectionSingleton INSTANCE = new ReflectionSingleton();

    private ReflectionSingleton() {
        if (INSTANCE != null) {
            throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
        }
    }

    public static ReflectionSingleton getInstance() {
        return INSTANCE;
    }
}
