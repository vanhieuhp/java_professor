package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.dto.ProductDTO;
import dev.hieunv.bankos.model.Product;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.FlushModeProductService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FlushModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FlushModeProductServiceImpl implements FlushModeProductService {

    private final ProductRepository productRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    @Transactional
    public List<ProductDTO> saveThenQueryBroken(String name, BigDecimal price, int stock) {
        entityManager.setFlushMode(FlushModeType.COMMIT); // Don't flush until commit

        // step 1: save new product
        Product newProduct = new Product(name, price, stock);
        productRepository.save(newProduct); // This won't be visible to queries yet

        // step 2: query all products
        List<Product> products = productRepository.findAll(); // This won't see the new product!

        return products.stream()
                .map(p -> ProductDTO.builder()
                        .name(p.getName())
                        .price(p.getPrice())
                        .stock(p.getStock())
                        .build())
                .toList();
    }

    // FIX 1 — revert to AUTO mode (default)
    @Transactional
    @Override
    public List<ProductDTO> saveThenQueryFixed(String name, BigDecimal price, int stock) {
        // AUTO mode — Hibernate flushes before any query touching product table
        entityManager.setFlushMode(FlushModeType.AUTO);

        Product newProduct = new Product(name, price, stock);
        productRepository.save(newProduct);

        // Hibernate auto-flushes before this query ✅
        List<Product> products = productRepository.findAll();

        return products.stream()
                .map(p -> ProductDTO.builder()
                        .name(p.getName())
                        .price(p.getPrice())
                        .stock(p.getStock())
                        .build())
                .toList();
    }

    // FIX 2 — keep COMMIT mode but flush explicitly before query
    @Transactional
    @Override
    public List<ProductDTO> saveThenQueryExplicitFlush(String name, BigDecimal price, int stock) {
        entityManager.setFlushMode(FlushModeType.COMMIT);

        Product newProduct = new Product(name, price, stock);
        productRepository.save(newProduct);

        // Explicit flush — force write to DB before query
        entityManager.flush(); // ← manually flush

        List<Product> products = productRepository.findAll();

        return products.stream()
                .map(p -> ProductDTO.builder()
                        .name(p.getName())
                        .price(p.getPrice())
                        .stock(p.getStock())
                        .build())
                .toList();
    }
}
