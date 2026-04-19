package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.product.ProductDTO;
import dev.hieunv.bankos.dto.product.ProductSearchDTO;
import dev.hieunv.bankos.model.Product;
import org.springframework.data.domain.Page;

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

    Page<ProductDTO> searchProducts(ProductSearchDTO dto, int page, int size);
}
