package dev.hieunv.bankos.client;

import org.springframework.stereotype.Component;

@Component
public class ExternalWarehouseClient {
    public void reserveStock(Long productId, int quantity) {
        try {
            // Simulate slow external HTTP call — 2 seconds
            Thread.sleep(2000);
            System.out.println("Stock reserved for product: " + productId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Warehouse call interrupted");
        }
    }
}
