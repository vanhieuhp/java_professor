package designpatterns.observer.weather.display;

import designpatterns.observer.weather.DisplayElement;
import designpatterns.observer.weather.Observer;
import designpatterns.observer.weather.WeatherData;

public class StatisticsDisplay implements Observer, DisplayElement {
    private float maxTemp = 0.0f;
    private float minTemp = 200;
    private float tempSum = 0.0f;
    private int numReadings;
    private WeatherData weatherData;

    public StatisticsDisplay(WeatherData weatherData) {
        this.weatherData = weatherData;
        weatherData.registerObserver(this);
    }

    public void update() {
        tempSum += weatherData.getTemperature();
        numReadings++;

        if (weatherData.getTemperature() > maxTemp) {
            maxTemp = weatherData.getTemperature();
        }

        if (weatherData.getTemperature() < minTemp) {
            minTemp = weatherData.getTemperature();
        }

        display();
    }

    public void display() {
        System.out.println("Avg/Max/Min temperature = " + (tempSum / numReadings)
                + "/" + maxTemp + "/" + minTemp);
    }
}

