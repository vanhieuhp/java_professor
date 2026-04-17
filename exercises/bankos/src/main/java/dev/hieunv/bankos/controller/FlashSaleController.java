package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.service.FlashSaleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/flash-sale")
@Tag(name = "Flash Sale", description = "Flash sale stock decrement operations")
public class FlashSaleController {

    private final FlashSaleService flashSaleService;

    @PostMapping("/{productId}/buy")
    @Operation(summary = "Buy product (atomic SQL decrement — no optimistic lock)")
    public ResponseEntity<String> buy(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int quantity) {
        try {
            flashSaleService.decrementStock(productId, quantity);
            return ResponseEntity.ok("Purchase successful");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/{productId}/buy-safe")
    @Operation(summary = "Buy product (optimistic lock + retry)")
    public ResponseEntity<String> buySafe(
            @PathVariable Long productId,
            @RequestParam(defaultValue = "1") int quantity) {
        try {
            flashSaleService.decrementStockSafe(productId, quantity);
            return ResponseEntity.ok("Purchase successful");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
