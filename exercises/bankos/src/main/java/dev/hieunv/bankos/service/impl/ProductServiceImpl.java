package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.client.ExternalWarehouseClient;
import dev.hieunv.bankos.dto.product.ProductDTO;
import dev.hieunv.bankos.dto.product.ProductSearchDTO;
import dev.hieunv.bankos.dto.product.ProductSpecification;
import dev.hieunv.bankos.model.Product;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ExternalWarehouseClient warehouseClient;

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

    @Transactional
    @Override
    public ProductDTO createProductBroken(ProductDTO dto) {
        // Step 1 — save product (connection acquired here)
        Product product = new Product(dto.getName(), dto.getPrice(), dto.getStock());
        productRepository.save(product);

        // Step 2 — slow HTTP call (2 seconds!) connection still held!
        warehouseClient.reserveStock(product.getId(), dto.getStock());

        // Step 3 — update status
        product.setStatus("RESERVED");

        return toDTO(product);
    }

    @Override
    public ProductDTO createProductFixed(ProductDTO dto) {
        // Step 1 — save in short transaction → connection acquired and released immediately
        Long productId = saveProductInTx(dto);

        // Step 2 — HTTP call outside transaction — no connection held ✅
        warehouseClient.reserveStock(productId, dto.getStock());

        // Step 3 — update status in new short transaction
        return updateStatusInTx(productId, "RESERVED");
    }

    @Transactional
    public Long saveProductInTx(ProductDTO dto) {
        Product product = new Product(dto.getName(), dto.getPrice(), dto.getStock());
        productRepository.save(product);
        return product.getId();
        // transaction commits here → connection returned to pool immediately ✅
    }

    @Transactional
    public ProductDTO updateStatusInTx(Long productId, String status) {
        Product product = productRepository.findById(productId).orElseThrow();
        product.setStatus(status);
        return toDTO(product);
        // transaction commits here → connection returned to pool immediately ✅
    }

    private ProductDTO toDTO(Product product) {
        return ProductDTO.builder()
                .name(product.getName())
                .price(product.getPrice())
                .stock(product.getStock())
                .build();
    }

    @Override
    public Page<ProductDTO> searchProducts(ProductSearchDTO filters, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("name").ascending());
        Page<Product> products = productRepository.findAll(
                ProductSpecification.withFilters(filters), pageable);

        return products.map(p -> ProductDTO.builder()
                .name(p.getName())
                .price(p.getPrice())
                .stock(p.getStock())
                .build());
    }
}
