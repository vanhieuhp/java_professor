package dev.hieunv.two_databases.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hieunv.two_databases.common.AggregateType;
import dev.hieunv.two_databases.common.EventType;
import dev.hieunv.two_databases.common.LedgerStatus;
import dev.hieunv.two_databases.common.Status;
import dev.hieunv.two_databases.domain.primary.LocalLedgerEntry;
import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.domain.primary.TransferSaga;
import dev.hieunv.two_databases.domain.secondary.ExternalLedgerEntry;
import dev.hieunv.two_databases.dto.CompensationPayload;
import dev.hieunv.two_databases.dto.TransferPayload;
import dev.hieunv.two_databases.repository.primary.LocalLedgerRepository;
import dev.hieunv.two_databases.repository.primary.OutboxEventRepository;
import dev.hieunv.two_databases.repository.primary.TransferSagaRepository;
import dev.hieunv.two_databases.repository.secondary.ExternalLedgerRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
public class SagaOrchestrationServiceImpl implements SagaOrchestrationService {

    @Autowired
    private TransferSagaRepository sagaRepository;
    @Autowired
    private LocalLedgerRepository localLedgerRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;
    @Autowired
    private ExternalLedgerRepository externalLedgerRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Transactional(transactionManager = "primaryTransactionManager")
    @Override
    public TransferSaga startTransfer(Long fromAccountId,
                                      Long toAccountId,
                                      BigDecimal amount) {

        System.out.println("\n[Saga] Starting transfer "
                + fromAccountId + " → " + toAccountId
                + " $" + amount);

        TransferSaga saga = sagaRepository.save(TransferSaga.builder()
                .fromAccountId(fromAccountId)
                .toAccountId(toAccountId)
                .amount(amount)
                .status(Status.STARTED)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build());

        localLedgerRepository.save(LocalLedgerEntry.builder()
                .accountId(fromAccountId)
                .amount(amount)
                .status(LedgerStatus.DEBITED)
                .createdAt(LocalDateTime.now())
                .build());

        saga.markDebitCompleted();
        sagaRepository.save(saga);

        outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType(AggregateType.TRANSFER)
                .aggregateId(saga.getId().toString())
                .eventType(EventType.DEBIT_COMPLETED)
                .payload(toJson(new TransferPayload(
                        saga.getId().toString(), toAccountId, amount)))
                .idempotencyKey("CREDIT-" + saga.getId())
                .createdAt(LocalDateTime.now())
                .build());

        System.out.println("[Saga] id=" + saga.getId()
                + " status=" + saga.getStatus());
        System.out.println("[Saga] Debit + outbox committed atomically ✅");

        return saga;
    }

    @Transactional(transactionManager = "secondaryTransactionManager")
    @Override
    public void completeCreditStep(OutboxEvent event,
                                   boolean simulateCreditFailure) {

        TransferPayload transferPayload = fromJson(event.getPayload(), TransferPayload.class);
        System.out.println("\n[Saga] Processing credit for sagaId=" + transferPayload.sagaId());

        if (simulateCreditFailure) {
            System.out.println("[Saga] 💥 Credit FAILED — "
                    + "external bank rejected transaction!");
            throw new RuntimeException(
                    "External bank rejected: account closed");
        }

        if (externalLedgerRepository.existsByIdempotencyKey(event.getIdempotencyKey())) {
            System.out.println("[Saga] Duplicate credit detected — skipping");
            return;
        }

        externalLedgerRepository.save(ExternalLedgerEntry.builder()
                .accountId(transferPayload.toAccountId())
                .amount(transferPayload.amount())
                .status(LedgerStatus.CREDITED)
                .idempotencyKey(event.getIdempotencyKey())
                .createdAt(LocalDateTime.now())
                .build());

        System.out.println("[Saga] Credit completed ✅ "
                + "toAccount=" + transferPayload.toAccountId()
                + " $" + transferPayload.amount());
    }

    @Transactional(transactionManager = "primaryTransactionManager")
    @Override
    public void handleCreditResult(UUID sagaId, boolean creditSucceeded,
                                   String failureReason) {

        TransferSaga saga = sagaRepository.findById(sagaId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Saga not found: " + sagaId));

        if (creditSucceeded) {
            saga.markCreditCompleted();
            sagaRepository.save(saga);
            System.out.println("[Saga] ✅ COMPLETED id=" + sagaId);
        } else {
            saga.markCompensating(failureReason);
            sagaRepository.save(saga);

            outboxEventRepository.save(OutboxEvent.builder()
                    .aggregateType(AggregateType.TRANSFER_COMPENSATION)
                    .aggregateId(sagaId.toString())
                    .eventType(EventType.COMPENSATE_DEBIT)
                    .payload(toJson(new CompensationPayload(
                            sagaId.toString(), saga.getFromAccountId(),
                            saga.getAmount(), failureReason)))
                    .idempotencyKey(saga.getCompensationKey())
                    .createdAt(LocalDateTime.now())
                    .build());

            System.out.println("[Saga] ⚠️  COMPENSATING id=" + sagaId
                    + " reason=" + failureReason);
        }
    }

    @Transactional(transactionManager = "primaryTransactionManager")
    @Override
    public void executeCompensation(OutboxEvent compensationEvent) {

        String compensationKey = compensationEvent.getIdempotencyKey();

        if (localLedgerRepository.existsByIdempotencyKey(compensationKey)) {
            System.out.println("[Compensation] Already compensated key="
                    + compensationKey + " → skipping");
            return;
        }

        CompensationPayload payload = fromJson(
                compensationEvent.getPayload(), CompensationPayload.class);

        localLedgerRepository.save(LocalLedgerEntry.builder()
                .accountId(payload.fromAccountId())
                .amount(payload.amount())
                .status(LedgerStatus.COMPENSATION_CREDIT)
                .idempotencyKey(compensationKey)
                .createdAt(LocalDateTime.now())
                .build());

        sagaRepository.findById(UUID.fromString(payload.sagaId()))
                .ifPresent(saga -> {
                    saga.markCompensated();
                    sagaRepository.save(saga);
                });

        System.out.println("[Compensation] ✅ $" + payload.amount()
                + " returned to account " + payload.fromAccountId());
        System.out.println("[Compensation] Saga status → COMPENSATED");
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to deserialize payload", e);
        }
    }
}
