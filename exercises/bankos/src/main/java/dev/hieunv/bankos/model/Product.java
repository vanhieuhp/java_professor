package dev.hieunv.bankos.model;

import dev.hieunv.bankos.enums.ProductStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Cache;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "products")
@Cache(usage = CacheConcurrencyStrategy.READ_WRITE) // ← enable L2 cache
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "product_seq")
    @SequenceGenerator(name = "product_seq", allocationSize = 50)
    private Long id;

    private String name;
    private BigDecimal price;
    private int stock;

    @Enumerated(EnumType.STRING)
    private ProductStatus status;

    private String sagaId;

    @Version
    private Long version; // Hibernate adds WHERE id=? AND version=? to every UPDATE

    public Product(String name, BigDecimal price, int stock) {
        this.name = name;
        this.price = price;
        this.stock = stock;
        this.status = ProductStatus.AVAILABLE;
    }
}
