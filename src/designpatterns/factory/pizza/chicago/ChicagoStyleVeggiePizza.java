package designpatterns.factory.pizza.chicago;

import designpatterns.factory.pizza.Pizza;

public class ChicagoStyleVeggiePizza extends Pizza {

    public ChicagoStyleVeggiePizza() {
        setName("Chicago Style Veggie Pizza");
        setDough("Extra Thick Crust Dough");
        setSauce("Plum Tomato Sauce");

        getToppings().add("Shredded Mozzarella Cheese");
        getToppings().add("Black Olives");
        getToppings().add("Spinach");
        getToppings().add("Eggplant");
    }

    void cut() {
        System.out.println("Cutting the pizza into square slices");
    }
}
