package designpatterns.singleton;

import java.io.Serializable;

public class Singleton implements Serializable {

    private static final long serialVersionUID = 1L;

    private static Singleton uniqueInstance;

    private Singleton() {
        if (uniqueInstance != null) {
            throw new RuntimeException("Use getInstance() method to get the unique instance of this class.");
        }
    }

    public static Singleton getInstance() {
        if (uniqueInstance == null) {
            synchronized (Singleton.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new Singleton();
                }
            }
        }

        return uniqueInstance;
    }

    protected Object readResolve() {
        return getInstance();
    }
}
