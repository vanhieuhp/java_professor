package designpatterns.factory.pizza.chicago;

import designpatterns.factory.pizza.Pizza;
import designpatterns.factory.pizza.PizzaStore;

public class ChicagoPizzaStore extends PizzaStore {

    public Pizza createPizza(String type) {
        return switch (type) {
            case "cheese" -> new ChicagoStyleCheesePizza();
            case "veggie" -> new ChicagoStyleVeggiePizza();
            case "clam" -> new ChicagoStyleClamPizza();
            case "pepperoni" -> new ChicagoStylePepperoniPizza();
            default -> null;
        };
    }
}
