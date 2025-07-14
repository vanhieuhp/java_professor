package designpatterns.factory.pizza.chicago;

import designpatterns.factory.pizza.Pizza;

public class ChicagoStyleCheesePizza extends Pizza {

    public ChicagoStyleCheesePizza() {
        setName("Chicago Style Sauce and Cheese Pizza");
        setDough("Extra Thick Crust Dough");
        setSauce("Plum Tomato Sauce");
        getToppings().add("Shredded Mozzarella Cheese");
    }

    void cut() {
        System.out.println("Cutting the pizza into square slices");
    }
}
