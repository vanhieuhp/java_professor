package designpatterns.compound.beat;

public interface HeartModelInterface {
    int getHeartRate();
    void registerObserver(BPMObserver o);
    void removeObserver(BPMObserver o);
    void registerObserver(BeatObserver o);
    void removeObserver(BeatObserver o);
}
