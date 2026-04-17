package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.ProductDTO;

import java.math.BigDecimal;
import java.util.List;

public interface FlushModeProductService {
    List<ProductDTO> saveThenQueryBroken(String name, BigDecimal price, int stock);
    List<ProductDTO> saveThenQueryFixed(String name, BigDecimal price, int stock);
    List<ProductDTO> saveThenQueryExplicitFlush(String name, BigDecimal price, int stock);
}
