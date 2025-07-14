package designpatterns.decorator.pizza;

public class ThickcrustPizzaDecorator extends PizzaDecorator {

    public ThickcrustPizzaDecorator() {
        description = "Thick crust pizza, with tomato sauce";
    }

    public double cost() {
        return 7.99;
    }
}
