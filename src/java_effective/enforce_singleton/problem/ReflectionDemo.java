package java_effective.enforce_singleton.problem;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

public class ReflectionDemo {
    static class ReflectionVulnerable {
        public static ReflectionVulnerable INSTANCE = new ReflectionVulnerable();
        private String id = "VULNERABLE_INSTANCE";
        private ReflectionVulnerable() {
            System.out.println("[CONSTRUCTOR] Private constructor called!");
        }

        public static ReflectionVulnerable getInstance() {
            return INSTANCE;
        }

        public String getId() {
            return id;
        }

    }

    static class ReflectionProtected {
        public static ReflectionProtected INSTANCE = new ReflectionProtected();
        private String id = "PROTECTED_INSTANCE";
        private ReflectionProtected() {
            if (INSTANCE != null) {
                throw new RuntimeException("Use getInstance() method to get the single instance of this class.");
            }
        }

        public static ReflectionProtected getInstance() {
            if (INSTANCE == null) {
                synchronized (ReflectionProtected.class) {
                    if (INSTANCE == null) {
                        INSTANCE = new ReflectionProtected();
                    }
                }
            }

            return INSTANCE;
        }

        public String getId() {
            return id;
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("DEMO 3: REFLECTION VULNERABLE");
        System.out.println("=".repeat(60));

        System.out.println("\n>>> Test 1: VULNERABLE Singleton:\n");
        ReflectionVulnerable normal = ReflectionVulnerable.getInstance();
        System.out.println("Normal getInstance(): " + System.identityHashCode(normal));

        System.out.println("\n  Attempting to break using reflection...");
        try {
            Constructor<ReflectionVulnerable> constructor = ReflectionVulnerable.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            ReflectionVulnerable viaReflection = constructor.newInstance();
            System.out.println("Via reflection: " + System.identityHashCode(viaReflection));

            System.out.println("  Same as original? " + (normal == viaReflection));

            if (normal != viaReflection) {
                System.out.println("\n  ❌ BUG! Reflection bypassed the singleton!");
                System.out.println("     A second instance was created!");
            }
        } catch (Exception e) {
            System.out.println("Reflection failed: " + e.getMessage());
        }

        System.out.println("\n" + "-".repeat(60));
        System.out.println("\n>>> Test 2: PROTECTED Singleton (constructor check):\n");

        ReflectionProtected normal1 = ReflectionProtected.getInstance();
        System.out.println("  Normal getInstance(): " + System.identityHashCode(normal1));

        System.out.println("\n  Attempting to break using reflection...");

        try {
            Constructor<ReflectionProtected> constructor =
                    ReflectionProtected.class.getDeclaredConstructor();
            constructor.setAccessible(true);

            // This will throw IllegalStateException!
            ReflectionProtected viaReflection = constructor.newInstance();

            System.out.println("  Created via reflection: " +
                    System.identityHashCode(viaReflection));

        } catch (IllegalStateException e) {
            System.out.println("  ✅ Blocked! " + e.getMessage());
        } catch (Exception e) {
            System.out.println("  Reflection failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // --------------------------------------------
        // Bonus: Can we modify the INSTANCE field?
        // --------------------------------------------
        System.out.println("\n" + "-".repeat(60));
        System.out.println("\n>>> Bonus: Can we modify the static INSTANCE field?\n");

        try {
            Field field = ReflectionVulnerable.class.getField("INSTANCE");
            System.out.println("  Original INSTANCE: " +
                    System.identityHashCode(ReflectionVulnerable.INSTANCE));

            // Try to set it to null
            field.set(null, null);
            System.out.println("  Set INSTANCE to null!");

            // Now getInstance will create a NEW instance
            ReflectionVulnerable afterNull = ReflectionVulnerable.getInstance();
            System.out.println("  New instance after null: " +
                    System.identityHashCode(afterNull));
            System.out.println("  ❌ Singleton can be destroyed via reflection!");

        } catch (Exception e) {
            System.out.println("  Field modification failed: " + e.getMessage());
        }

        System.out.println("\n" + "=".repeat(60));
    }
}
