package lambda;

interface I {
    void m();
}

public class BasicLambdas {
    public static void main(String[] args) {
        I i = new I() {
            @Override
            public void m() {
                System.out.println("Hello World!");
            }
        };
        i.m();

        // Java 8 - Lambda expression
        I lambdaI = () -> {
            System.out.println("Lambda version");
        };

        System.out.println("Xin Chao Ban Linh Dan");

        I lambdaI2 = () -> System.out.println("Lambda version 2");
        lambdaI.m();
        lambdaI2.m();
    }
}