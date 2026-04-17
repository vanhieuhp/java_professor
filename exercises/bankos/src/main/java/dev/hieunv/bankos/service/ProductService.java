package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.ProductDTO;
import dev.hieunv.bankos.model.Product;

import java.math.BigDecimal;
import java.util.List;

public interface ProductService {

    Product create(String name, BigDecimal price, int stock);

    Product findById(Long id);

    List<Product> findAll();

    Product updatePrice(Long id, BigDecimal newPrice);

    void purchaseStock(Long id, int quantity);

    ProductDTO createProductBroken(ProductDTO dto);

    ProductDTO createProductFixed(ProductDTO dto);
}
