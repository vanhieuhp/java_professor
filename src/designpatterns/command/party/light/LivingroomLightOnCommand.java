package designpatterns.command.party.light;

import designpatterns.command.party.Command;

public class LivingroomLightOnCommand implements Command {

    Light light;

    public LivingroomLightOnCommand(Light light) {
        this.light = light;
    }
    public void execute() {
        light.on();
    }
    public void undo() {
        light.off();
    }
}
