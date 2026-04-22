package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.model.Order;
import dev.hieunv.bankos.repository.OrderRepository;
import dev.hieunv.bankos.repository.ProductRepository;
import dev.hieunv.bankos.service.SagaStepService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaStepServiceImpl implements SagaStepService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    @Override
    public int reserveStock(String sagaId, OrderRequest request) {
        return productRepository.claimSemanticLock(
                request.getProductId(), sagaId, request.getQuantity());
    }

    @Transactional
    @Override
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
    @Override
    public void confirmSale(String sagaId, Order order, OrderRequest request) {
        productRepository.confirmSale(
                request.getProductId(), sagaId, request.getQuantity());
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
    }

    @Transactional
    @Override
    public void compensate(String sagaId, Order order) {
        productRepository.releaseSemanticLock(order.getProductId(), sagaId);
        order.setStatus("COMPENSATED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);
        log.info("[Saga:{}] Compensated — semantic lock released", sagaId);
    }
}
