package java_effective.enforce_singleton.exercise;

import java.io.ObjectStreamException;
import java.io.Serializable;

public class DatabaseConnection implements Serializable {
    public static final DatabaseConnection INSTANCE = new DatabaseConnection();
    private static final Long serialVersionUID = 1L;
    private String url = "jdbc:mysql://localhost:3306/mydb";

    private DatabaseConnection() {}

    public String getUrl() {
        return url;
    }
    /*
    What you need to do: Add proper serialization protection
    Expected outcome: Serialization + deserialization should return the SAME instance
    Hint: Implement readResolve() method
*/

    public static synchronized DatabaseConnection getInstance() {
        return INSTANCE;
    }

    private Object readResolve() throws ObjectStreamException {
        System.out.println( "Deserializing DatabaseConnection");
        return getInstance();
    }
}
