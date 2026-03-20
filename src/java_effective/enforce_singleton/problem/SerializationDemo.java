package java_effective.enforce_singleton.problem;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;

public class SerializationDemo {

    // ============================================================
    // ❌ BROKEN: Singleton broken by serialization
    // ============================================================
    static class BrokenSingleton implements Serializable {
        private static final long serialVersionUID = 1L;
        private static BrokenSingleton INSTANCE = new BrokenSingleton();
        private String id = "ORIGINAL_INSTANCE";

        private BrokenSingleton() {
            System.out.println("[CONSTRUCTOR] Creating new instance");
        }

        public static BrokenSingleton getInstance() {
            return INSTANCE;
        }

        public String getId() {
            return id;
        }
    }

    // ============================================================
    // ✅ FIXED: Singleton protected with readResolve()
    // ============================================================
    static class FixedSingleton implements Serializable {
        private static final long serialVersionUID = 1L;
        private static volatile FixedSingleton INSTANCE;
        private String id = "ORIGINAL_INSTANCE";

        private FixedSingleton() {
            System.out.println("  [CONSTRUCTOR] Creating new instance");
        }

        public static FixedSingleton getInstance() {
            if (INSTANCE == null) {
                synchronized (FixedSingleton.class) {
                    if (INSTANCE == null) {
                        INSTANCE = new FixedSingleton();
                    }
                }
            }
            return INSTANCE;
        }

        public String getId() {
            return id;
        }

        // ✅ KEY FIX: Called during deserialization
        private Object readResolve() throws ObjectStreamException {
            System.out.println("  [readResolve] Returning existing instance!");
            return getInstance();
        }
    }

    public static void main(String[] args) throws Exception{
        System.out.println("=".repeat(60));
        System.out.println("DEMO 2: SERIALIZATION VULNERABILITY");
        System.out.println("=".repeat(60));

        // --------------------------------------------
        // Test 1: Broken Singleton
        // --------------------------------------------
        System.out.println("\n>>> Test 1: BROKEN Singleton (no readResolve):\n");
        BrokenSingleton original = BrokenSingleton.getInstance();
        System.out.println("  Original instance ID: " + original.getId());
        System.out.println("  Original hashCode: " + System.identityHashCode(original));

        // Serialize to byte array
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(original);
        oos.close();

        // Deserialize - creates NEW instance without readResolve!
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        ObjectInputStream ois = new ObjectInputStream(bais);
        BrokenSingleton deserialized = (BrokenSingleton) ois.readObject();
        ois.close();

        System.out.println("\n  After deserialization:");
        System.out.println("  Deserialized hashCode: " + System.identityHashCode(deserialized));
        System.out.println("  Same instance? " + (original == deserialized));

        if (original != deserialized) {
            System.out.println("\n  ❌ BUG! Deserialization created a NEW instance!");
            System.out.println("     The singleton property is BROKEN!");
        }

        // --------------------------------------------
        // Test 2: Fixed Singleton
        // --------------------------------------------
        System.out.println("\n" + "-".repeat(60));
        System.out.println("\n>>> Test 2: FIXED Singleton (with readResolve):\n");

        FixedSingleton originalFixed = FixedSingleton.getInstance();
        System.out.println("  Original hashCode: " + System.identityHashCode(originalFixed));

        baos = new ByteArrayOutputStream();
        oos = new ObjectOutputStream(baos);
        oos.writeObject(originalFixed);
        oos.close();

        bais = new ByteArrayInputStream(baos.toByteArray());
        ois = new ObjectInputStream(bais);
        FixedSingleton deserializedFixed = (FixedSingleton) ois.readObject();
        ois.close();

        System.out.println("  Deserialized hashCode: " + System.identityHashCode(deserializedFixed));
        System.out.println("  Same instance? " + (originalFixed == deserializedFixed));

        if (originalFixed == deserializedFixed) {
            System.out.println("\n  ✅ readResolve() correctly returns existing instance!");
        }
    }

}
