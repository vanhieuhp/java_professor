package designpatterns.singleton;

import java.io.Serializable;

public class ChocolateBoiler implements Serializable {

    private boolean empty;
    private boolean boiled;
    private static final long serialVersionUID = 1L;
    private static ChocolateBoiler uniqueInstance;

    private ChocolateBoiler() {
        if (uniqueInstance != null) {
            throw new RuntimeException("Use getInstance() method to get the unique instance of this class.");
        }

        empty = true;
        boiled = false;
    }

    public static ChocolateBoiler getInstance() {
        if (uniqueInstance == null) {
            synchronized (ChocolateBoiler.class) {
                if (uniqueInstance == null) {
                    uniqueInstance = new ChocolateBoiler();
                }
            }
        }
        return uniqueInstance;
    }

    public void fill() {
        if (isEmpty()) {
            empty = false;
            boiled = false;
        }
    }

    public void drain() {
        if (!isEmpty() && isBoiled()) {
            // drain the boiled milk and chocolate
            empty = true;
        }
    }

    public void boil() {
        if (!isEmpty() && !isBoiled()) {
            // bring the contents to a boil
            boiled = true;
        }
    }

    public boolean isEmpty() {
        return empty;
    }

    public boolean isBoiled() {
        return boiled;
    }
}
