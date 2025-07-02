package designpatterns.observer.weather.display;

import designpatterns.observer.weather.DisplayElement;
import designpatterns.observer.weather.Observer;
import designpatterns.observer.weather.WeatherData;

public class CurrentConditionsDisplay implements Observer, DisplayElement {

    private float temp;
    private float humidity;
    private WeatherData weatherData;

    public CurrentConditionsDisplay(WeatherData weatherData) {
        this.weatherData = weatherData;
        weatherData.registerObserver(this);
    }

    @Override
    public void display() {
        System.out.println( "Current conditions: " + temp + "F degrees and " + humidity + "% humidity");
    }

    @Override
    public void update() {
        this.temp = weatherData.getTemperature();
        this.humidity = weatherData.getHumidity();
        display();
    }
}
