package designpatterns.factory.pizza.ny;

import designpatterns.factory.pizza.Pizza;
import designpatterns.factory.pizza.PizzaStore;

public class NYPizzaStore extends PizzaStore {

    public Pizza createPizza(String type) {
        return switch (type) {
            case "cheese" -> new NYStyleCheesePizza();
            case "veggie" -> new NYStyleVeggiePizza();
            case "clam" -> new NYStyleClamPizza();
            case "pepperoni" -> new NYStylePepperoniPizza();
            default -> null;
        };
    }
}
