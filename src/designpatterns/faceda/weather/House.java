package designpatterns.faceda.weather;

public class House {

    private final WeatherStation weatherStation;

    public House(WeatherStation weatherStation) {
        this.weatherStation = weatherStation;
    }

    public double getTemperature() {
        return weatherStation.getTemperature();
    }
}
