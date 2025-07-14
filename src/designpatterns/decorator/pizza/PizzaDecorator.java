package designpatterns.decorator.pizza;

public abstract class PizzaDecorator {

    String description = "Unknown Pizza";

    public String getDescription() {
        return description;
    }

    public abstract double cost();
}
