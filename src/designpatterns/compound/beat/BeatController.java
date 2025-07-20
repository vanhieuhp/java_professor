package designpatterns.compound.beat;

public class BeatController implements ControllerInterface {

    BeatModelInterface model;
    DJView view;

    public BeatController(BeatModelInterface model) {
        this.model = model;
        this.view = new DJView(this, model);
        view.createView();
        view.createControls();
        view.disableStopMenuItem();
        view.disableStartMenuItem();
        model.initialize();
    }

    @Override
    public void start() {
        model.on();
        view.disableStartMenuItem();
        view.enabledStopMenuItem();
    }

    @Override
    public void stop() {
        model.off();
        view.disableStopMenuItem();
        view.enableStartMenuItem();
    }

    @Override
    public void increaseBPM() {
        int bpm = model.getBPM();
        bpm++;
        model.setBPM(bpm);
    }

    @Override
    public void decreaseBPM() {
        int bpm = model.getBPM();
        bpm--;
        model.setBPM(bpm);
    }

    @Override
    public void setBPM(int bpm) {
        model.setBPM(bpm);
    }
}
