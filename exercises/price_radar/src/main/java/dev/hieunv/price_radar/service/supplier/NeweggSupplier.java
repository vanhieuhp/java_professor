package dev.hieunv.price_radar.service.supplier;

import dev.hieunv.price_radar.model.PriceResult;
import dev.hieunv.price_radar.service.Supplier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class NeweggSupplier implements Supplier {

    @Override
    public PriceResult getPrice(String productName) {
        long start = System.currentTimeMillis();
        simulateDelay();
        double price = 780 + ThreadLocalRandom.current().nextDouble(0, 220); // $780–$1000
        return new PriceResult("Newegg", productName, price, System.currentTimeMillis() - start);
    }

    private void simulateDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(200, 2001));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}