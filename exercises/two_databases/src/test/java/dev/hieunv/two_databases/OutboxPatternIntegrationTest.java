package dev.hieunv.two_databases;

import dev.hieunv.two_databases.common.EventType;
import dev.hieunv.two_databases.common.LedgerStatus;
import dev.hieunv.two_databases.common.OutboxStatus;
import dev.hieunv.two_databases.domain.primary.LocalLedgerEntry;
import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.domain.secondary.ExternalLedgerEntry;
import dev.hieunv.two_databases.repository.primary.LocalLedgerRepository;
import dev.hieunv.two_databases.repository.primary.OutboxEventRepository;
import dev.hieunv.two_databases.repository.secondary.ExternalLedgerRepository;
import dev.hieunv.two_databases.service.InterBankTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;

@SpringBootTest
@Transactional(propagation = Propagation.NEVER)
public class OutboxPatternIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private InterBankTransferService transferService;
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
    }

    @Test
    void demonstrateOutboxAtomicity_crashBeforeRelay() {
        System.out.println("=== OUTBOX — CRASH BEFORE RELAY ===");
        System.out.println("App crashes after commit — outbox saves us\n");

        String transferId = transferService.transferWithOutbox(
                1L, 2L, new BigDecimal("500.00"));

        // ── State immediately after commit ────────────────────

        List<LocalLedgerEntry> debits = localLedgerRepository.findAll();
        List<OutboxEvent> events = outboxEventRepository.findAll();
        List<ExternalLedgerEntry> credits = externalLedgerRepository.findAll();

        System.out.println("\n=== STATE AFTER COMMIT (before relay) ===");
        System.out.println("[Primary DB]   debit entries:  " + debits.size());
        System.out.println("[Outbox table] pending events: " + events.size());
        System.out.println("[Secondary DB] credit entries: " + credits.size());

        // Debit and outbox event committed atomically
        assertThat(debits).hasSize(1);
        assertThat(debits.get(0).getStatus()).isEqualTo(LedgerStatus.DEBITED);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(events.get(0).getEventType()).isEqualTo(EventType.DEBIT_COMPLETED);

        // Secondary DB not yet written — relay hasn't run
        assertThat(credits).isEmpty();

        System.out.println("\n✅ Outbox saved us — even if app crashes NOW,");
        System.out.println("   relay will deliver the event on restart!");
    }

    @Test
    void demonstrateRelayDelivery_secondaryDbUpdated() {
        System.out.println("=== OUTBOX — RELAY DELIVERS EVENT ===\n");

        // Step 1 — write debit + outbox event atomically
        transferService.transferWithOutbox(1L, 2L, new BigDecimal("500.00"));

        // Step 2 — relay delivers the pending event
        transferService.relayPendingEvents();

        // Step 3 — verify both DBs are consistent
        List<LocalLedgerEntry> debits = localLedgerRepository.findAll();
        List<OutboxEvent> events = outboxEventRepository.findAll();
        List<ExternalLedgerEntry> credits = externalLedgerRepository.findAll();

        System.out.println("\n=== STATE AFTER RELAY ===");
        System.out.println("[Primary DB]   debit:  "
                + debits.get(0).getStatus()
                + " $" + debits.get(0).getAmount());
        System.out.println("[Outbox table] event:  "
                + events.get(0).getStatus());
        System.out.println("[Secondary DB] credit: "
                + credits.get(0).getStatus()
                + " $" + credits.get(0).getAmount());

        // ── Assertions ────────────────────────────────────────

        // Both DBs consistent
        assertThat(debits).hasSize(1);
        assertThat(credits).hasSize(1);

        // Same amount on both sides
        assertThat(debits.get(0).getAmount())
                .isEqualByComparingTo(credits.get(0).getAmount());

        // Outbox event marked delivered
        assertThat(events.get(0).getStatus()).isEqualTo(OutboxStatus.DELIVERED);

        // Statuses correct
        assertThat(debits.get(0).getStatus()).isEqualTo(LedgerStatus.DEBITED);
        assertThat(credits.get(0).getStatus()).isEqualTo(LedgerStatus.CREDITED);

        System.out.println("\n✅ Both DBs consistent after relay!");
    }

    @Test
    void demonstrateIdempotency_relayRunsTwice() {
        System.out.println("=== OUTBOX — IDEMPOTENT RELAY ===");
        System.out.println("Relay crashes after delivery but before marking DELIVERED");
        System.out.println("Relay runs again — must not double-credit\n");

        // Step 1 — commit debit + outbox
        transferService.transferWithOutbox(1L, 2L, new BigDecimal("500.00"));

        // Step 2 — relay delivers successfully
        transferService.relayPendingEvents();

        // Step 3 — relay runs AGAIN (simulates retry after crash)
        // Manually reset event to PENDING to simulate relay re-delivery
        OutboxEvent event = outboxEventRepository.findAll().get(0);

        // Force event back to PENDING — simulates relay crash before marking
        // In production this would happen if relay crashed post-delivery
        // but pre-update. We simulate it by calling deliverToSecondaryDb again.
        System.out.println("\n[Relay] Crashed after delivery — retrying...");
        transferService.deliverToSecondaryDb(event);

        // ── Check secondary DB ─────────────────────────────────
        List<ExternalLedgerEntry> credits = externalLedgerRepository.findAll();

        System.out.println("\n=== STATE AFTER DOUBLE RELAY ===");
        System.out.println("[Secondary DB] credit entries: " + credits.size());
        credits.forEach(c -> System.out.println("  → "
                + c.getStatus() + " $" + c.getAmount()
                + " key=" + c.getIdempotencyKey()));

        // ── Assertions — idempotency held ─────────────────────

        // Only ONE credit despite relay running twice
        assertThat(credits).hasSize(1);
        assertThat(credits.get(0).getAmount())
                .isEqualByComparingTo("500.00");

        System.out.println("\n✅ Idempotency held — $500 credited exactly once"
                + " despite relay running twice!");
    }

    @Test
    void demonstrateFullFlow_compareWith2PCFailure()
            throws InterruptedException {
        System.out.println("=== FULL COMPARISON ===");
        System.out.println("2PC:    crash → $500 gone forever");
        System.out.println("Outbox: crash → relay recovers automatically\n");

        // With Outbox — commit + relay
        transferService.transferWithOutbox(
                1L, 2L, new BigDecimal("500.00"));

        // Simulate app restart — relay picks up PENDING events
        System.out.println("[App restart] Relay scanning for PENDING events...");
        transferService.relayPendingEvents();

        // Both DBs consistent
        long primaryCount = localLedgerRepository.count();
        long secondaryCount = externalLedgerRepository.count();
        long pendingEvents = outboxEventRepository
                .findByStatusOrderByCreatedAtAsc(OutboxStatus.PENDING).size();

        System.out.println("\n[Primary DB]   entries: " + primaryCount);
        System.out.println("[Secondary DB] entries: " + secondaryCount);
        System.out.println("[Outbox]   pending:     " + pendingEvents);

        // ── Assertions ────────────────────────────────────────

        // Both sides have exactly one entry
        assertThat(primaryCount).isEqualTo(1);
        assertThat(secondaryCount).isEqualTo(1);

        // No pending events — all delivered
        assertThat(pendingEvents).isZero();

        System.out.println("\n✅ Outbox guarantees consistency even after crashes!");
        System.out.println("   This is what 2PC cannot do across independent DBs.");
    }
}
