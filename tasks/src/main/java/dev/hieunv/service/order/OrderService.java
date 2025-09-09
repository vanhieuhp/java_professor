package dev.hieunv.service.order;

import dev.hieunv.domain.dto.order.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@RequiredArgsConstructor
@Service
public class OrderService {

    private final ApplicationEventPublisher eventPublisher;

    public void createOrder(String orderId) {
        // business logic to create order
        System.out.println("Order created: " + orderId);

        // publish event
        eventPublisher.publishEvent(new OrderCreatedEvent(orderId));
    }


}
