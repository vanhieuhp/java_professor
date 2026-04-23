package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentFailedEvent;
import dev.hieunv.bankos.enums.OrderStatus;
import dev.hieunv.bankos.enums.ProductStatus;
import dev.hieunv.bankos.model.Order;
import dev.hieunv.bankos.repository.OrderRepository;
import dev.hieunv.bankos.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderCompensationConsumer {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    @KafkaListener(
            topics = "payment-failed",
            groupId = "bankos-order-compensation-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentFailedEvent event = (PaymentFailedEvent) record.value();
        String sagaId   = event.getSagaId();
        Long productId  = event.getProductId();

        // Idempotency check
        Order order = orderRepository.findBySagaId(sagaId).orElseThrow();
        if (order.getStatus() == OrderStatus.COMPENSATED) {
            log.info("[PAYMENT_COMPENSATED][Saga:{}] Already compensated — skipping", sagaId);
            return;
        }

        productRepository.releaseSemanticLock(productId, sagaId, ProductStatus.AVAILABLE);
        order.setStatus(OrderStatus.COMPENSATED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("[PAYMENT_COMPENSATED][Saga:{}] Order COMPENSATED — stock released", sagaId);
    }
}
