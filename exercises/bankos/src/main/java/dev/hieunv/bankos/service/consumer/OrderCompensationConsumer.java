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
public class OrderCompensationConsumer {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @KafkaListener(
            topics = "payment-failed",
            groupId = "bankos-order-compensation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        Map<String, Object> payload = (Map<String, Object>) record.value();
        String sagaId  = payload.get("sagaId").toString();
        Long productId = Long.valueOf(payload.get("productId").toString());

        // Idempotency check
        Order order = orderRepository.findBySagaId(sagaId).orElseThrow();
        if ("COMPENSATED".equals(order.getStatus())) {
            log.info("[Saga:{}] Already compensated — skipping", sagaId);
            return;
        }

        productRepository.releaseSemanticLock(productId, sagaId);
        order.setStatus("COMPENSATED");
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("[Saga:{}] Order COMPENSATED — stock released", sagaId);
    }
}
