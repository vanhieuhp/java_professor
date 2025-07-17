package designpatterns.command.party.tv;

import designpatterns.command.party.Command;

public class TVOnCommand implements Command {

    TV tv;

    public TVOnCommand(TV tv) {
        this.tv= tv;
    }

    public void execute() {
        tv.on();
        tv.setInputChannel();
    }

    public void undo() {
        tv.off();
    }
}
