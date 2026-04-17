package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.ProductDTO;
import dev.hieunv.bankos.service.FlushModeProductService;
import dev.hieunv.bankos.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final FlushModeProductService flushModeProductService;
    private final ProductService productService;

    @PostMapping("/flush/broken")
    public List<ProductDTO> saveThenQueryBroken(@RequestBody ProductDTO dto) {
        return flushModeProductService.saveThenQueryBroken(
                dto.getName(), dto.getPrice(), dto.getStock());
    }

    @PostMapping("/flush/fixed-auto")
    public List<ProductDTO> saveThenQueryFixed(@RequestBody ProductDTO dto) {
        return flushModeProductService.saveThenQueryFixed(
                dto.getName(), dto.getPrice(), dto.getStock());
    }

    @PostMapping("/flush/fixed-explicit")
    public List<ProductDTO> saveThenQueryExplicitFlush(@RequestBody ProductDTO dto) {
        return flushModeProductService.saveThenQueryExplicitFlush(
                dto.getName(), dto.getPrice(), dto.getStock());
    }

    @PostMapping("/broken")
    public ProductDTO createProductBroken(@RequestBody ProductDTO dto) {
        return productService.createProductBroken(dto);
    }

    @PostMapping("/fixed")
    public ProductDTO createProductFixed(@RequestBody ProductDTO dto) {
        return productService.createProductFixed(dto);
    }
}
