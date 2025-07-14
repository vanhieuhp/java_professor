package designpatterns.decorator.pizza;

public class OlivesDecorator extends ToppingDecorator {

    public OlivesDecorator(PizzaDecorator pizzaDecorator) {
        super.pizzaDecorator = pizzaDecorator;
    }

    public String getDescription() {
        return pizzaDecorator.getDescription() + ", Olives";
    }

    public double cost() {
        return pizzaDecorator.cost() + 0.50;
    }
}
