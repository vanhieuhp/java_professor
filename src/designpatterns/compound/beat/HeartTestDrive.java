package designpatterns.compound.beat;

public class HeartTestDrive {

    public static void main(String[] args) {
        HeartModel heartModel = new HeartModel();
        HeartController heartController = new HeartController(heartModel);
    }
}
