package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.model.Product;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.FlashSaleService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class FlashSaleServiceImpl implements FlashSaleService {

    private final ProductRepository productRepository;

    @Transactional
    @Override
    public void decrementStock(Long productId, int quantity) {
//        Product p = productRepository.findById(productId).orElseThrow();
//        if (p.getStock() < quantity) {
//            throw new IllegalStateException("Not enough stock!");
//        }
//        p.setStock(p.getStock() - quantity);
        int updated = productRepository.decrementStock(productId, quantity);
        if (updated == 0) throw new IllegalStateException("Out of stock");
    }

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    @Override
    public void decrementStockSafe(Long productId, int quantity) {
        Product p = productRepository.findById(productId).orElseThrow();
        if (p.getStock() < quantity) {
            throw new IllegalStateException("Not enough stock!");
        }
        p.setStock(p.getStock() - quantity);
        productRepository.save(p);
    }

    @Recover
    @Override
    public void recover(ObjectOptimisticLockingFailureException e, Long productId, int quantity) {
        throw new IllegalStateException("Out of stock after retries — too much contention");
    }

    @Transactional
    @Override
    public Long seedStock(int stock) {
        Product product = new Product("Flash Sale Item", new java.math.BigDecimal("99.99"), stock);
        productRepository.save(product);
        log.info("[FlashSale] Seeded product id={} stock={}", product.getId(), stock);
        return product.getId();
    }
}
