package designpatterns.singleton.serialization;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

public class SerializationTest {

    public static void main(String[] args) throws Exception {
        SerializationSingleton instance1 = SerializationSingleton.getInstance();

        // serialize
        ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream("singleton.ser"));
        out.writeObject(instance1);
        out.close();

        // Deserialize
        ObjectInputStream in = new ObjectInputStream(new FileInputStream("singleton.ser"));
        SerializationSingleton instance2 = (SerializationSingleton) in.readObject();
        in.close();

        System.out.println("Instance 1: " + instance1);
        System.out.println("Instance 2: " + instance2);
    }
}
