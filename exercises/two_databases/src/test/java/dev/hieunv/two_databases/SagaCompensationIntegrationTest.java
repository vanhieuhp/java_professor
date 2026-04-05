package dev.hieunv.two_databases;

import dev.hieunv.two_databases.common.LedgerStatus;
import dev.hieunv.two_databases.common.OutboxStatus;
import dev.hieunv.two_databases.common.Status;
import dev.hieunv.two_databases.domain.primary.LocalLedgerEntry;
import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.domain.primary.TransferSaga;
import dev.hieunv.two_databases.repository.primary.LocalLedgerRepository;
import dev.hieunv.two_databases.repository.primary.OutboxEventRepository;
import dev.hieunv.two_databases.repository.primary.TransferSagaRepository;
import dev.hieunv.two_databases.repository.secondary.ExternalLedgerRepository;
import dev.hieunv.two_databases.service.SagaOrchestrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional(propagation = Propagation.NEVER)
public class SagaCompensationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SagaOrchestrationService sagaService;
    @Autowired
    private TransferSagaRepository sagaRepository;
    @Autowired
    private LocalLedgerRepository localLedgerRepository;
    @Autowired
    private ExternalLedgerRepository externalLedgerRepository;
    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @BeforeEach
    void cleanup() {
        outboxEventRepository.deleteAll();
        localLedgerRepository.deleteAll();
        externalLedgerRepository.deleteAll();
        sagaRepository.deleteAll();
    }

    @Test
    void happyPath_transferCompletes() {
        System.out.println("=== SAGA HAPPY PATH ===\n");

        // Step 1 — start saga
        TransferSaga saga = sagaService.startTransfer(
                1L, 2L, new BigDecimal("500.00"));

        assertThat(saga.getStatus()).isEqualTo(Status.DEBIT_COMPLETED);

        // Step 2 — deliver credit (no failure)
        OutboxEvent event = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).getFirst();
        sagaService.completeCreditStep(event, false);

        // Step 3 — mark saga complete
        sagaService.handleCreditResult(saga.getId(), true, null);

        // ── Assertions ────────────────────────────────────────
        TransferSaga completed = sagaRepository.findById(saga.getId()).orElseThrow();

        assertThat(completed.getStatus()).isEqualTo(Status.CREDIT_COMPLETED);

        assertThat(localLedgerRepository.findAll()).hasSize(1);
        assertThat(localLedgerRepository.findAll().getFirst().getStatus())
                .isEqualTo(LedgerStatus.DEBITED);

        assertThat(externalLedgerRepository.findAll()).hasSize(1);
        assertThat(externalLedgerRepository.findAll().getFirst().getStatus())
                .isEqualTo(LedgerStatus.CREDITED);

        System.out.println("✅ Saga COMPLETED — both sides consistent");
    }

    @Test
    void failurePath_compensationRestoresMoney() {
        System.out.println("=== SAGA COMPENSATION — CREDIT FAILS ===\n");

        // Step 1 — start saga, debit committed
        TransferSaga saga = sagaService.startTransfer(
                1L, 2L, new BigDecimal("500.00"));

        // Step 2 — credit FAILS
        // The credit outbox event remains PENDING (credit rolled back)
        OutboxEvent creditEvent = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).getFirst();

        try {
            sagaService.completeCreditStep(creditEvent, true);
        } catch (RuntimeException e) {
            System.out.println("[Test] Credit failed: " + e.getMessage());
        }

        // Step 3 — trigger compensation
        sagaService.handleCreditResult(
                saga.getId(), false,
                "External bank rejected: account closed");

        // Verify saga is COMPENSATING
        TransferSaga compensating = sagaRepository.findById(saga.getId()).orElseThrow();
        assertThat(compensating.getStatus()).isEqualTo(Status.COMPENSATING);
        assertThat(compensating.getFailureReason()).contains("account closed");

        // Step 4 — execute compensation (credit money back)
        // There are now 2 PENDING events: the original credit event (index 0)
        // and the new compensation event (index 1, created after)
        OutboxEvent compensationEvent = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).get(1);
        sagaService.executeCompensation(compensationEvent);

        // ── Assertions ────────────────────────────────────────
        TransferSaga compensated = sagaRepository.findById(saga.getId()).orElseThrow();

        // Saga fully compensated
        assertThat(compensated.getStatus()).isEqualTo(Status.COMPENSATED);

        // Primary DB has two entries: original debit + compensation credit
        List<LocalLedgerEntry> primaryEntries = localLedgerRepository.findAll();
        assertThat(primaryEntries).hasSize(2);

        // One DEBITED, one COMPENSATION_CREDIT
        assertThat(primaryEntries)
                .extracting(LocalLedgerEntry::getStatus)
                .containsExactlyInAnyOrder(LedgerStatus.DEBITED, LedgerStatus.COMPENSATION_CREDIT);

        // Net effect on primary = zero ($500 debited, $500 credited back)
        BigDecimal netEffect = primaryEntries.stream()
                .map(e -> e.getStatus() == LedgerStatus.DEBITED
                        ? e.getAmount().negate()
                        : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netEffect).isEqualByComparingTo("0.00");

        // Secondary DB is empty — credit never happened
        assertThat(externalLedgerRepository.findAll()).isEmpty();

        System.out.println("\n✅ COMPENSATION SUCCESSFUL");
        System.out.println("   Net effect on customer: $0 (money returned)");
        System.out.println("   Secondary DB: empty (credit never happened)");
    }

    @Test
    void compensationIsIdempotent_runsTwiceSafely() {
        System.out.println("=== COMPENSATION IDEMPOTENCY ===\n");
        System.out.println("Compensation relay crashes — runs twice");
        System.out.println("Customer must NOT receive $1000 back\n");

        // Setup — trigger compensation
        TransferSaga saga = sagaService.startTransfer(
                1L, 2L, new BigDecimal("500.00"));

        OutboxEvent creditEvent = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).getFirst();

        try {
            sagaService.completeCreditStep(creditEvent, true);
        } catch (RuntimeException ignored) {}

        sagaService.handleCreditResult(saga.getId(), false, "Credit failed");

        // The compensation event is the second PENDING event (index 1)
        OutboxEvent compensationEvent = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).get(1);

        // First compensation run
        System.out.println("[Relay] Running compensation first time...");
        sagaService.executeCompensation(compensationEvent);

        // Second compensation run — simulates relay retry
        System.out.println("[Relay] Relay crashed — running again...");
        sagaService.executeCompensation(compensationEvent);

        // ── Assertions ────────────────────────────────────────

        // Only ONE compensation credit despite running twice
        long compensationCount = localLedgerRepository.findAll().stream()
                .filter(e -> e.getStatus() == LedgerStatus.COMPENSATION_CREDIT)
                .count();

        assertThat(compensationCount).isEqualTo(1);

        // Net effect still zero
        BigDecimal netEffect = localLedgerRepository.findAll().stream()
                .map(e -> e.getStatus() == LedgerStatus.DEBITED
                        ? e.getAmount().negate()
                        : e.getAmount())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(netEffect).isEqualByComparingTo("0.00");

        System.out.println("\n✅ Compensation idempotent — "
                + "customer received $500 back exactly once");
    }
}
