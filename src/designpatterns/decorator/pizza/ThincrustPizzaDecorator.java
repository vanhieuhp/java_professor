package designpatterns.decorator.pizza;

public class ThincrustPizzaDecorator extends PizzaDecorator {

    public ThincrustPizzaDecorator() {
        description = "Thin crust pizza, with tomato sauce";
    }

    public double cost() {
        return 7.99;
    }
}
