package dev.hieunv.bankos.controller;

import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.dto.order.OrderResponse;
import dev.hieunv.bankos.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Saga orchestration with semantic locking")
public class OrderController {
    private final OrderService orderService;

    @PostMapping
    @Operation(summary = "Place order — Saga with semantic lock")
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        int status = response.getStatus().equals("COMPLETED") ? 200 : 409;
        return ResponseEntity.status(status).body(response);
    }

    @PostMapping("/{sagaId}/compensate")
    @Operation(summary = "Manually trigger compensation for a Saga")
    public ResponseEntity<OrderResponse> compensate(@PathVariable String sagaId) {
        return ResponseEntity.ok(orderService.compensate(sagaId));
    }
}
