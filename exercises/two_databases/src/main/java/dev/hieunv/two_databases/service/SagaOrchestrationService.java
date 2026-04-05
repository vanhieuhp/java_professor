package dev.hieunv.two_databases.service;

import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.domain.primary.TransferSaga;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

public interface SagaOrchestrationService {
    @Transactional(transactionManager = "primaryTransactionManager")
    TransferSaga startTransfer(Long fromAccountId,
                               Long toAccountId,
                               BigDecimal amount);

    @Transactional(transactionManager = "secondaryTransactionManager")
    void completeCreditStep(OutboxEvent event,
                            boolean simulateCreditFailure);

    @Transactional(transactionManager = "primaryTransactionManager")
    void handleCreditResult(UUID sagaId, boolean creditSucceeded,
                            String failureReason);

    @Transactional(transactionManager = "primaryTransactionManager")
    void executeCompensation(OutboxEvent compensationEvent);
}
