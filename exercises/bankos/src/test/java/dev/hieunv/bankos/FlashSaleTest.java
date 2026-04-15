package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Product;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.FlashSaleService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class FlashSaleTest {

    @Autowired
    FlashSaleService flashSaleService;
    @Autowired
    ProductRepository productRepository;

    @Test
    void concurrent_decrement_never_goes_negative() throws InterruptedException {
        // Seed product with stock = 1
        Product p = productRepository.save(new Product("Widget", BigDecimal.TEN, 1));

        int threads = 100;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threads);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    flashSaleService.decrementStockSafe(p.getId(), 1);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        Product result = productRepository.findById(p.getId()).orElseThrow();

        // Only 1 success, 99 failures — stock never goes negative
        assertEquals(1, successCount.get());
        assertEquals(99, failCount.get());
        assertEquals(0, result.getStock()); // ✅ exactly 0, never negative
    }
}
