package designpatterns.decorator.pizza;

public abstract class ToppingDecorator extends PizzaDecorator {

    PizzaDecorator pizzaDecorator;

    public abstract String getDescription();
}
