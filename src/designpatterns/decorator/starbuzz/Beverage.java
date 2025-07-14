package designpatterns.decorator.starbuzz;

public abstract class Beverage {

    public enum Size {TALL, GRANDE, VENTI};
    Size size = Size.TALL;

    public Size getSize() {
        return size;
    }

    String description = "Unknown Beverage";

    public String getDescription() {
        return description;
    }

    public abstract double cost();
}
