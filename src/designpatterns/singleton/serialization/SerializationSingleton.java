package designpatterns.singleton.serialization;

import java.io.Serializable;

public class SerializationSingleton implements Serializable {

    private static final long serialVersionUID = 1L;
    private static SerializationSingleton INSTANCE = new SerializationSingleton();

    private SerializationSingleton() {}

    public static SerializationSingleton getInstance() {
        return INSTANCE;
    }

    protected Object readResolve() {
        return getInstance();
    }
}
