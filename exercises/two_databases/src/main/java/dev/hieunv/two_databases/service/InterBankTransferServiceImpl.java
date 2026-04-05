package dev.hieunv.two_databases.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.hieunv.two_databases.common.AggregateType;
import dev.hieunv.two_databases.common.EventType;
import dev.hieunv.two_databases.common.LedgerStatus;
import dev.hieunv.two_databases.common.OutboxStatus;
import dev.hieunv.two_databases.domain.primary.LocalLedgerEntry;
import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.domain.secondary.ExternalLedgerEntry;
import dev.hieunv.two_databases.dto.TransferPayload;
import dev.hieunv.two_databases.repository.primary.LocalLedgerRepository;
import dev.hieunv.two_databases.repository.primary.OutboxEventRepository;
import dev.hieunv.two_databases.repository.secondary.ExternalLedgerRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class InterBankTransferServiceImpl implements InterBankTransferService {

    @Autowired
    private LocalLedgerRepository localLedgerRepository;

    @Autowired
    private ExternalLedgerRepository externalLedgerRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("primaryTransactionManager")
    private PlatformTransactionManager primaryManager;

    @Autowired
    @Qualifier("secondaryTransactionManager")
    private PlatformTransactionManager secondaryManager;

    @Override
    public void transferBroken(Long fromAccountId,
                               Long toAccountId,
                               BigDecimal amount,
                               boolean simulateCrash) {
        System.out.println("=== INTER-BANK TRANSFER (BROKEN) ===");

        TransactionTemplate primaryTx = new TransactionTemplate(primaryManager);
        LocalLedgerEntry debit = primaryTx.execute(status -> {
            LocalLedgerEntry entry = localLedgerRepository.save(LocalLedgerEntry.builder()
                    .accountId(fromAccountId)
                    .amount(amount)
                    .status(LedgerStatus.DEBITED)
                    .createdAt(LocalDateTime.now())
                    .build());
            System.out.println("[Primary DB] Debit committed ✅" + " entry id=" + entry.getId());
            return entry;
        });

        if (simulateCrash) {
            System.out.println("APP CRASHED — primary committed, secondary never received commit!");
            throw new RuntimeException("Simulated crash between DB1 commit and DB2 commit");
        }

        TransactionTemplate secondaryTx = new TransactionTemplate(secondaryManager);

        secondaryTx.execute(status -> {
            ExternalLedgerEntry entry = externalLedgerRepository.save(ExternalLedgerEntry.builder()
                    .accountId(toAccountId)
                    .amount(amount)
                    .status(LedgerStatus.CREDITED)
                    .createdAt(LocalDateTime.now())
                    .build());
            System.out.println("[Secondary DB] Credit committed " + " entry id=" + entry.getId());
            return entry;
        });

        System.out.println("Transfer complete");
    }

    @Transactional(transactionManager = "primaryTransactionManager")
    @Override
    public String transferWithOutbox(Long fromAccountId,
                                     Long toAccountId,
                                     BigDecimal amount) {
        String transferId = UUID.randomUUID().toString();
        String idempotencyKey = "CREDIT-" + UUID.randomUUID();

        System.out.println("=== INTER-BANK TRANSFER (OUTBOX) ===");

        LocalLedgerEntry debit = localLedgerRepository.save(LocalLedgerEntry.builder()
                .accountId(fromAccountId)
                .amount(amount)
                .status(LedgerStatus.DEBITED)
                .createdAt(LocalDateTime.now())
                .build());
        System.out.println("[Primary DB] Debit committed ✅" + " entry id=" + debit.getId());

        try {
            String payload = objectMapper.writeValueAsString(new TransferPayload(transferId, toAccountId, amount));
            OutboxEvent event = OutboxEvent.builder()
                    .aggregateType(AggregateType.TRANSFER)
                    .aggregateId(transferId)
                    .eventType(EventType.DEBIT_COMPLETED)
                    .payload(payload)
                    .idempotencyKey(idempotencyKey)
                    .createdAt(LocalDateTime.now())
                    .build();

            outboxEventRepository.save(event);
            System.out.println("[Outbox] Event saved id=" + event.getId() + " status=" + event.getStatus());
            System.out.println("[Outbox] Both debit + event committed atomically");
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize outbox payload", e);
        }

        return transferId;
    }

    @Scheduled(fixedDelay = 1000) // every 1 second
    @Transactional(transactionManager = "secondaryTransactionManager")
    @Override
    public void relayPendingEvents() {
        List<OutboxEvent> pending = outboxEventRepository.findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }

        System.out.println("[Relay] Found " + pending.size() + " pending event(s)");
        pending.forEach(event -> {
            try {
                deliverToSecondaryDb(event);
                event.markDelivered();
                outboxEventRepository.save(event);
                System.out.println("[Relay] Event " + event.getId() + " marked as DELIVERED");
            } catch (Exception e) {
                System.out.println(e.getMessage());
                event.markFailed();
                outboxEventRepository.save(event);
                System.out.println("[Relay] Event " + event.getId() + " marked as FAILED");
            }
        });
    }

    @Transactional(transactionManager = "secondaryTransactionManager")
    @Override
    public void deliverToSecondaryDb(OutboxEvent event) {
        boolean alreadyProcessed = externalLedgerRepository.existsByIdempotencyKey(event.getIdempotencyKey());

        if (alreadyProcessed) {
            System.out.println("[Consumer] Duplicate detected key=" + event.getIdempotencyKey() + " → skipping");
            return;
        }

        try {
            TransferPayload payload = objectMapper.readValue(event.getPayload(), TransferPayload.class);

            ExternalLedgerEntry externalLedgerEntry = ExternalLedgerEntry.builder()
                    .accountId(payload.toAccountId())
                    .amount(payload.amount())
                    .status(LedgerStatus.CREDITED)
                    .idempotencyKey(event.getIdempotencyKey())
                    .createdAt(LocalDateTime.now())
                    .build();
            externalLedgerRepository.save(externalLedgerEntry);

            System.out.println("[Consumer] Credit saved → toAccount=" + payload.toAccountId() + " amount=$" + payload.amount());
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize outbox payload", e);
        }
    }
}
