package designpatterns.faceda.weather;

public class WeatherStation {

    private final Thermometer thermometer;

    public WeatherStation(Thermometer thermometer) {
        this.thermometer = thermometer;
    }

    public double getTemperature() {
        return thermometer.getTemperature();
    }
}
