package designpatterns.state.gumballstate;

public interface State {
    void insertQuarter();
    void ejectQuarter();
    void turnCrank();
    void dispense();

    public void refill();
}
