package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.model.Order;

public interface SagaStepService {
    int reserveStock(String sagaId, OrderRequest request);

    Order createOrder(String sagaId, OrderRequest request);

    void confirmSale(String sagaId, Order order, OrderRequest request);

    void compensate(String sagaId, Order order);
}
