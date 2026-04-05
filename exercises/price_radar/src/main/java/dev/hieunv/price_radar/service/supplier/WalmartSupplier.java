package dev.hieunv.price_radar.service.supplier;

import dev.hieunv.price_radar.model.PriceResult;
import dev.hieunv.price_radar.service.Supplier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

@Component
public class WalmartSupplier implements Supplier {

    @Override
    public PriceResult getPrice(String productName) {
        long start = System.currentTimeMillis();
        simulateDelay();
        double price = 820 + ThreadLocalRandom.current().nextDouble(0, 160); // $820–$980
        return new PriceResult("Walmart", productName, price, System.currentTimeMillis() - start);
    }

    private void simulateDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(200, 2001));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}