package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.payment.PaymentCompletedEvent;
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
public class OrderConfirmConsumer {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;

    @Transactional
    @KafkaListener(
            topics = "payment-completed",
            groupId = "bankos-order-confirm-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        PaymentCompletedEvent event = (PaymentCompletedEvent) record.value();
        String sagaId   = event.getSagaId();
        Long productId  = event.getProductId();
        int quantity    = event.getQuantity();

        // Idempotency check
        Order order = orderRepository.findBySagaId(sagaId).orElseThrow();
        if (order.getStatus() == OrderStatus.COMPLETED) {
            log.info("[PAYMENT_COMPLETED][Saga:{}] Already completed — skipping", sagaId);
            return;
        }

        productRepository.confirmSale(productId, sagaId, quantity, ProductStatus.SOLD);
        order.setStatus(OrderStatus.COMPLETED);
        order.setUpdatedAt(LocalDateTime.now());
        orderRepository.save(order);

        log.info("[PAYMENT_COMPLETED][Saga:{}] Order COMPLETED", sagaId);
    }
}
