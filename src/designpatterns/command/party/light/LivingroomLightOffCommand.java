package designpatterns.command.party.light;

import designpatterns.command.party.Command;

public class LivingroomLightOffCommand implements Command {

    Light light;

    public LivingroomLightOffCommand(Light light) {
        this.light = light;
    }
    public void execute() {
        light.off();
    }
    public void undo() {
        light.on();
    }
}
