package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.dto.order.OrderResponse;

public interface OrderService {

    // Happy path — reserve stock → charge payment → confirm
    OrderResponse placeOrder(OrderRequest request);

    // Compensation — release semantic lock + refund if payment already charged
    OrderResponse compensate(String sagaId);
}
