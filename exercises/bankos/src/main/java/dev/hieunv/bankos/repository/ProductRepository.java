package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
}
