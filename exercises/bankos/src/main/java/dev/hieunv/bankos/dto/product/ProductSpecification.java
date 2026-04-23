package dev.hieunv.bankos.dto.product;

import dev.hieunv.bankos.enums.ProductStatus;
import dev.hieunv.bankos.model.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

public class ProductSpecification {

    public static Specification<Product> hasName(String name) {
        return (root, query, cb) -> {
            if (StringUtils.hasText(name)) return null;

            String pattern = "%s" + name.trim().toLowerCase() + "%s";
            return cb.like(cb.lower(root.get("name")), pattern);
        };
    }

    public static Specification<Product> hasPriceGreaterThan(BigDecimal minPrice) {
        return (root, query, cb) -> {
            if (minPrice == null) {
                return null;
            } else {
                return cb.greaterThanOrEqualTo(root.get("price"), minPrice);
            }
        };
    }

    public static Specification<Product> hasPriceLessThan(BigDecimal maxPrice) {
        return (root, query, cb) -> {
            if (maxPrice == null) {
                return null;
            } else {
                return cb.lessThanOrEqualTo(root.get("price"), maxPrice);
            }
        };
    }

    public static Specification<Product> hasStockGreaterThan(int minStock) {
        return (root, query, cb) -> {
            if (minStock <= 0) {
                return null;
            } else {
                return cb.greaterThanOrEqualTo(root.get("stock"), minStock);
            }
        };
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> {
            if (status == null) return null;
            return cb.equal(root.get("status"), status);
        };
    }

    public static Specification<Product> withFilters(ProductSearchDTO filters) {
        return Specification
                .where(hasName(filters.getName()))
                .and(hasPriceGreaterThan(filters.getMinPrice()))
                .and(hasPriceLessThan(filters.getMaxPrice()))
                .and(hasStatus(filters.getStatus()))
                .and(hasStockGreaterThan(filters.getMinStock()));
    }
}
