package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.dto.order.OrderCreatedEvent;
import dev.hieunv.bankos.dto.order.OrderRequest;
import dev.hieunv.bankos.dto.order.OrderResponse;
import dev.hieunv.bankos.enums.OrderStatus;
import dev.hieunv.bankos.model.Order;
import dev.hieunv.bankos.service.OrderService;
import dev.hieunv.bankos.service.SagaStepService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final SagaStepService sagaStepService;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String ORDER_CREATED_TOPIC = "order-created";

    public OrderServiceImpl(SagaStepService sagaStepService,
                            @Qualifier("paymentKafkaTemplate")
                            KafkaTemplate<String, Object> kafkaTemplate) {
        this.sagaStepService = sagaStepService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public OrderResponse placeOrder(OrderRequest request) {
        String sagaId = UUID.randomUUID().toString();
        log.info("[Saga:{}] Starting — userId={} productId={}", sagaId, request.getUserId(), request.getProductId());

        // step 1: Reserve stock with semantic lock
        int claimed = sagaStepService.reserveStock(sagaId, request);
        if (claimed == 0) {
            log.warn("[Saga:{}] Stock unavailable or already locked", sagaId);
            return OrderResponse.builder()
                    .sagaId(sagaId)
                    .status(OrderStatus.FAILED)
                    .message("Product unavailable — already reserved or out of stock")
                    .build();
        }

        // step 2: create order
        Order order = sagaStepService.createOrder(sagaId, request);
        log.info("[Saga:{}] Stock reserved → Order ID={}", sagaId, order.getId());

        // step 3: publish ORDER_CREATED event instead of calling payment directly
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .sagaId(sagaId)
                .orderId(order.getId())
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .amount(request.getAmount())
                .occurredAt(LocalDateTime.now())
                .build();

        kafkaTemplate.send(ORDER_CREATED_TOPIC, sagaId, event);
        log.info("[Saga:{}] Published ORDER_CREATED event to Kafka", sagaId);

        // return immediately - payment happens asynchronously
        return OrderResponse.builder()
                .orderId(order.getId())
                .sagaId(sagaId)
                .status(OrderStatus.PENDING)
                .message("Order received — processing payment")
                .build();

    }

    @Override
    public OrderResponse compensate(String sagaId) {
        return OrderResponse.builder()
                .sagaId(sagaId)
                .status(OrderStatus.COMPENSATED)
                .message("Stock released")
                .build();
    }
}
