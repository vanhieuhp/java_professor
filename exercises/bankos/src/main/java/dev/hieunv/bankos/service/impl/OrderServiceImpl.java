package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.service.OrderService;
import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.dto.order.OrderResponse;
import dev.hieunv.bankos.model.Order;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.OrderRepository;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.OrderService;
import dev.hieunv.bankos.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentService paymentService;

    @Transactional
    @Override
    public OrderResponse placeOrder(OrderRequest request) {
        String sagaId = UUID.randomUUID().toString();
        log.info("[Saga:{}] Starting — userId={} productId={}",
                sagaId, request.getUserId(), request.getProductId());

        // step 1: Reserve stock with semantic lock
        int claimed = reserveStock(sagaId, request);
        if (claimed == 0) {
            log.warn("[Saga:{}] Stock unavailable or already locked", sagaId);
            return OrderResponse.builder()
                    .sagaId(sagaId)
                    .status("FAILED")
                    .message("Product unavailable — already reserved or out of stock")
                    .build();
        }

        // step 2: create order
        Order order = createOrder(sagaId, request);
        log.info("[Saga:{}] Stock reserved → Order ID={}", sagaId, order.getId());

        // step 3: charge payment
        try {
            if (request.isSimulatePaymentFailure()) {
                throw new RuntimeException("Simulated payment failure");
            }

            Payment payment = paymentService.processPayment(
                    request.getUserId(), request.getAmount());

            // ── Step 4: Confirm sale — reduce stock, mark SOLD ───────────
            confirmSale(sagaId, order, request);
            log.info("[Saga:{}] Payment OK → Order COMPLETED paymentId={}",
                    sagaId, payment.getId());

            return OrderResponse.builder()
                    .orderId(order.getId())
                    .sagaId(sagaId)
                    .status("COMPLETED")
                    .message("Order placed successfully")
                    .build();

        } catch (Exception e) {
            // ── Compensation: release semantic lock ───────────────────────
            log.warn("[Saga:{}] Payment failed → compensating: {}", sagaId, e.getMessage());
            compensate(sagaId);

            return OrderResponse.builder()
                    .orderId(order.getId())
                    .sagaId(sagaId)
                    .status("COMPENSATED")
                    .message("Payment failed — stock released")
                    .build();
        }

    }

    @Transactional
    @Override
    public OrderResponse compensate(String sagaId) {
        releaseStock(sagaId);
        updateOrderStatus(sagaId, "COMPENSATED");
        log.info("[Saga:{}] Compensated — semantic lock released", sagaId);
        return OrderResponse.builder()
                .sagaId(sagaId)
                .status("COMPENSATED")
                .message("Stock released")
                .build();
    }

    @Transactional
    public int reserveStock(String sagaId, OrderRequest request) {
        return productRepository.claimSemanticLock(
                request.getProductId(), sagaId, request.getQuantity());
    }

    @Transactional
    public Order createOrder(String sagaId, OrderRequest request) {
        return orderRepository.save(Order.builder()
                .userId(request.getUserId())
                .productId(request.getProductId())
                .status("STOCK_RESERVED")
                .sagaId(sagaId)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());
    }

    @Transactional
    public void confirmSale(String sagaId, Order order, OrderRequest request) {
        productRepository.confirmSale(
                request.getProductId(), sagaId, request.getQuantity());
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional
    public void releaseStock(String sagaId) {
        orderRepository.findBySagaId(sagaId).ifPresent(order -> {
            productRepository.releaseSemanticLock(order.getProductId(), sagaId);
            order.setStatus("COMPENSATED");
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }

    @Transactional
    public void updateOrderStatus(String sagaId, String status) {
        orderRepository.findBySagaId(sagaId).ifPresent(order -> {
            order.setStatus(status);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }
}
