package designpatterns.command.simpleremote;

public class LightOnCommand implements Command{
    private final Light light;

    public LightOnCommand(Light light) {
        this.light = light;
    }

    public void execute() {
        light.on();
    }
}
