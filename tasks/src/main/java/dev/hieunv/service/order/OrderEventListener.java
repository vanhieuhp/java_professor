package dev.hieunv.service.order;

import dev.hieunv.domain.dto.order.OrderCreatedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    @EventListener
    public void handleOrderCreated(OrderCreatedEvent event) {
        System.out.println("Received OrderCreatedEvent for order: " + event.getOrderId());
        // e.g., send email, update logs
    }
}
