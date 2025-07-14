package designpatterns.factory.pizza.chicago;

import designpatterns.factory.pizza.Pizza;

public class ChicagoStyleClamPizza extends Pizza {

    public ChicagoStyleClamPizza() {
        setName("Chicago Style Clam Pizza");
        setDough("Extra Thick Crust Dough");
        setSauce("Plum Tomato Sauce");
        getToppings().add("Shredded Mozzarella Cheese");
        getToppings().add("Frozen Clams from Chesapeake Bay");
    }

    void cut() {
        System.out.println("Cutting the pizza into square slices");
    }
}
