package designpatterns.templatemethod.coffee;

public class BeverageTest {

    public static void main(String[] args) {
//        Tea tea = new Tea();
//
//        tea.prepareRecipe();

        CoffeeWithHook coffeeHook = new CoffeeWithHook();

        System.out.println("Making coffee with hook");
        coffeeHook.prepareRecipe();
    }
}
