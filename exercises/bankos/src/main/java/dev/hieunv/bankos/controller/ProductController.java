package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.product.ProductDTO;
import dev.hieunv.bankos.dto.product.ProductSearchDTO;
import dev.hieunv.bankos.service.FlashSaleService;
import dev.hieunv.bankos.service.FlushModeProductService;
import dev.hieunv.bankos.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final FlushModeProductService flushModeProductService;
    private final ProductService productService;
    private final FlashSaleService flashSaleService;

    @PostMapping("/seed")
    public ResponseEntity<String> seed(@RequestParam(defaultValue = "100") int stock) {
        Long productId = flashSaleService.seedStock(stock);
        return ResponseEntity.ok("Seeded product id=" + productId + " stock=" + stock);
    }

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

    @GetMapping("/search")
    public Page<ProductDTO> searchProducts(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Integer minStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        ProductSearchDTO filters = ProductSearchDTO.builder()
                .name(name)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(status)
                .minStock(minStock)
                .build();

        return productService.searchProducts(filters, page, size);
    }
}
