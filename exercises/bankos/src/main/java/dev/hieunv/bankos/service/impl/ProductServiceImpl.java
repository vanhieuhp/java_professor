package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.model.Product;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Transactional
    @Override
    public Product create(String name, BigDecimal price, int stock) {
        return productRepository.save(new Product(name, price, stock));
    }

    @Transactional(readOnly = true)
    @Override
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    @Override
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    // Optimistic lock: Hibernate appends WHERE id=? AND version=? to the UPDATE.
    // If another transaction changed the row first, version won't match → ObjectOptimisticLockingFailureException.
    @Transactional
    @Override
    public Product updatePrice(Long id, BigDecimal newPrice) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));
        product.setPrice(newPrice);
        return productRepository.save(product);
    }

    @Transactional
    @Override
    public void purchaseStock(Long id, int quantity) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        if (product.getStock() < quantity) {
            throw new IllegalStateException("Insufficient stock for product: " + id);
        }

        product.setStock(product.getStock() - quantity);
        log.info("Purchased {} units of '{}', remaining stock: {}",
                quantity, product.getName(), product.getStock());

        // Hibernate will throw ObjectOptimisticLockingFailureException here
        // if another transaction already modified this row (version mismatch).
        productRepository.save(product);
    }
}
