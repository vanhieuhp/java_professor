package designpatterns.faceda.hometheater;

public class PopcornPopper {

    private String description;

    public PopcornPopper(String description) {
        this.description = description;
    }

    public void on() {
        System.out.println(description + " on");
    }

    public void off() {
        System.out.println(description + " off");
    }

    public String toString() {
        return description;
    }

    public void pop() {
        System.out.println(description + " popping popcorn!");
    }
}
