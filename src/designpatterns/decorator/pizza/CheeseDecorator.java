package designpatterns.decorator.pizza;

public class CheeseDecorator extends ToppingDecorator {

    public CheeseDecorator(PizzaDecorator pizzaDecorator) {
        super.pizzaDecorator = pizzaDecorator;
    }

    public String getDescription() {
        return pizzaDecorator.getDescription() + ", Cheese";
    }

    public double cost() {
        return pizzaDecorator.cost(); // cheese is free
    }
}
