package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.model.Order;
import dev.hieunv.bankos.repository.OrderRepository;
import dev.hieunv.bankos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderConfirmConsumer {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @KafkaListener(
            topics = "payment-completed",
            groupId = "bankos-order-confirm-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {

        Map<String, Object> payload = (Map<String, Object>) record.value();
        String sagaId   = payload.get("sagaId").toString();
        Long productId  = Long.valueOf(payload.get("productId").toString());
        int quantity    = Integer.parseInt(payload.get("quantity").toString());

        // Idempotency check
        Order order = orderRepository.findBySagaId(sagaId).orElseThrow();
        if ("COMPLETED".equals(order.getStatus())) {
            log.info("[Saga:{}] Already completed — skipping", sagaId);
            return;
        }

        productRepository.confirmSale(productId, sagaId, quantity);
        order.setStatus("COMPLETED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("[Saga:{}] Order COMPLETED", sagaId);
    }
}
