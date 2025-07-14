package designpatterns.decorator.pizza;

public class PizzaStoreDecorator {

    public static void main(String[] args) {
        PizzaDecorator pizzaDecorator = new ThincrustPizzaDecorator();
        pizzaDecorator = new CheeseDecorator(pizzaDecorator);
        pizzaDecorator = new OlivesDecorator(pizzaDecorator);
        System.out.println(pizzaDecorator.getDescription() + " $" + pizzaDecorator.cost());
    }
}
