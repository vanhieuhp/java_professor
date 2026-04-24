package dev.hieunv.bankos.service.consumer;

import dev.hieunv.bankos.dto.order.OrderCreatedEvent;
import dev.hieunv.bankos.dto.payment.PaymentCompletedEvent;
import dev.hieunv.bankos.dto.payment.PaymentFailedEvent;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.service.PaymentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@Slf4j
public class PaymentSagaConsumer {

    private final PaymentService paymentService;

    @Qualifier("paymentKafkaTemplate")
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment-completed";
    private static final String PAYMENT_FAILED_TOPIC    = "payment-failed";

    public PaymentSagaConsumer(PaymentService paymentService,
                               @Qualifier("paymentKafkaTemplate")
                               KafkaTemplate<String, Object> kafkaTemplate) {
        this.paymentService = paymentService;
        this.kafkaTemplate = kafkaTemplate;
    }

    @KafkaListener(
            topics = "order-created",
            groupId = "bankos-payment-saga-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(ConsumerRecord<String, Object> record) {
        OrderCreatedEvent event = (OrderCreatedEvent) record.value();
        log.info("[ORDER_CREATED][Saga:{}] Processing payment amount={}",
                event.getSagaId(), event.getAmount());

        try {
            Payment payment = paymentService.processPayment(
                    event.getUserId(), event.getAmount()
            );

            PaymentCompletedEvent paymentCompletedEvent = PaymentCompletedEvent.builder()
                    .sagaId(event.getSagaId())
                    .orderId(event.getOrderId())
                    .productId(event.getProductId())
                    .quantity(event.getQuantity())
                    .paymentId(payment.getId())
                    .amount(event.getAmount())
                    .occurredAt(LocalDateTime.now())
                    .build();
            // publish success
            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC, event.getSagaId(), paymentCompletedEvent);
            log.info("[ORDER_CREATED][Saga:{}] Published PAYMENT_COMPLETED event to Kafka", event.getSagaId());
        } catch (Exception e) {
            PaymentFailedEvent paymentFailedEvent = PaymentFailedEvent.builder()
                    .sagaId(event.getSagaId())
                    .orderId(event.getOrderId())
                    .productId(event.getProductId())
                    .reason(e.getMessage())
                    .occurredAt(LocalDateTime.now())
                    .build();

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC, event.getSagaId(), paymentFailedEvent);

            log.warn("[ORDER_CREATED][Saga:{}] Payment failed → published PAYMENT_FAILED reason={}",
                    event.getSagaId(), e.getMessage());
        }
    }
}
