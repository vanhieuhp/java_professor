package designpatterns.singleton.reflection;

import java.lang.reflect.Constructor;

public class ReflectionTest {

    public static void main(String[] args) throws Exception {
        ReflectionSingleton instance1 = ReflectionSingleton.getInstance();
        ReflectionSingleton instance2 = ReflectionSingleton.getInstance();
        System.out.println("instance1: " + instance1);
        System.out.println("instance2: " + instance2);
        System.out.println("==================================");

        Constructor<ReflectionSingleton> constructor = ReflectionSingleton.class.getDeclaredConstructor();
        constructor.setAccessible(true); // bypass private access

        ReflectionSingleton instance3 = constructor.newInstance();
        System.out.println("instance1: " + instance1);
        System.out.println("instance3: " + instance3);
    }

}
