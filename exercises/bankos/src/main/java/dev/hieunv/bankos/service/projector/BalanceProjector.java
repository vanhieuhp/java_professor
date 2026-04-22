package dev.hieunv.bankos.service.projector;

import dev.hieunv.bankos.dto.balance.BalanceReadModel;
import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import dev.hieunv.bankos.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class BalanceProjector {
    private final RedisTemplate<String, Object> redisTemplate;
    private final AccountRepository accountRepository;

    private static final String BALANCE_KEY_PREFIX = "balance:account:";
    private static final Duration BALANCE_TTL = Duration.ofHours(1);

    @KafkaListener(
            topics = "payment-events",
            groupId = "bankos-balance-projection-group"
    )
    public void project(ConsumerRecord<String, Object> record) {
        PaymentProcessedEvent event = (PaymentProcessedEvent) record.value();

        String redisKey = BALANCE_KEY_PREFIX + event.getAccountId();

        BalanceReadModel readModel = BalanceReadModel.builder()
                .accountId(event.getAccountId())
                .balance(event.getBalanceAfter())  // ← no DB query, no subtraction
                .lastPaymentId(event.getPaymentId())
                .lastUpdatedAt(LocalDateTime.now())
                .build();
        try {
            redisTemplate.opsForValue().set(redisKey, readModel, BALANCE_TTL);
            log.info("[CQRS] Balance projected accountId={} newBalance={}",
                    event.getAccountId(), readModel.getBalance());
        } catch (Exception e) {
            log.error("[CQRS] Failed to update Redis read model accountId={} error={}",
                    event.getAccountId(), e.getMessage());
            // Non-critical — DB is source of truth, Redis will be rebuilt on next event
        }
    }

}
