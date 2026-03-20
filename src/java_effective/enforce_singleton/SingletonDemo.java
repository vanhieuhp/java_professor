package java_effective.enforce_singleton;

public class SingletonDemo {

    public static void main(String[] args) {
        // Enum single - simplest
        Elvis elvis1 = Elvis.INSTANCE;
        Elvis elvis2 = Elvis.INSTANCE;
        System.out.println(elvis1 == elvis2);

        elvis1.sing();

        ElvisFinal ef1 = ElvisFinal.INSTANCE;
        ElvisFinal ef2 = ElvisFinal.INSTANCE;
        System.out.println(ef1 == ef2);
    }
}
