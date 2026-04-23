package dev.hieunv.bankos.service.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import dev.hieunv.bankos.dto.wallet.WalletStatusEvent;
import dev.hieunv.bankos.enums.OutboxEventStatus;
import dev.hieunv.bankos.model.OutboxEvent;
import dev.hieunv.bankos.repository.OutboxEventRepository;
import dev.hieunv.bankos.service.producer.PaymentEventProducer;
import dev.hieunv.bankos.service.producer.WalletStatusProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxEventProcessor {
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final PaymentEventProducer paymentEventProducer;
    private final WalletStatusProducer walletStatusProducer;

    private static final String PAYMENT_TOPIC = "payment-events";

    @Transactional
    public void processEvent(OutboxEvent event) {
        try {
            publishToKafka(event);
            event.setStatus(OutboxEventStatus.PUBLISHED);
            event.setPublishedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            log.info("[Relay] Published eventId={} type={} aggregateId={}",
                    event.getId(), event.getEventType(), event.getAggregateId());
        } catch (Exception e) {
            event.setRetryCount(event.getRetryCount() + 1);
            if (event.getRetryCount() >= 3) {
                event.setStatus(OutboxEventStatus.FAILED);
                log.error("[Relay] Event ID={} failed after 3 retries — marked FAILED",
                        event.getId());
            } else {
                log.warn("[Relay] Event ID={} publish failed retry={} — will retry",
                        event.getId(), event.getRetryCount());
            }
            outboxEventRepository.save(event);
        }
    }

    private void publishToKafka(OutboxEvent event) throws JsonProcessingException {
        Map<String, Object> payload = objectMapper.readValue(event.getPayload(), Map.class);

        if ("PAYMENT_PROCESSED".equals(event.getEventType())) {
            Long accountId      = Long.valueOf(payload.get("accountId").toString());
            BigDecimal amount   = new BigDecimal(payload.get("amount").toString());
            BigDecimal balanceAfter = new BigDecimal(payload.get("balanceAfter").toString());

            PaymentProcessedEvent kafkaEvent = PaymentProcessedEvent.builder()
                    .paymentId(event.getAggregateId())
                    .accountId(accountId)
                    .amount(amount)
                    .status(payload.get("status").toString())
                    .occurredAt(LocalDateTime.now())
                    .build();

            paymentEventProducer.sendPaymentEvent(kafkaEvent);

            // 2 — wallet-status (compacted topic)
            WalletStatusEvent walletEvent = WalletStatusEvent.builder()
                    .accountId(accountId)
                    .balance(balanceAfter)
                    .status("ACTIVE")
                    .occurredAt(LocalDateTime.now())
                    .build();
            walletStatusProducer.sendWalletStatus(walletEvent);
        }
    }
}
