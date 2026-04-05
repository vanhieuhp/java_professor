package dev.hieunv.two_databases;

import dev.hieunv.two_databases.repository.secondary.ExternalLedgerRepository;
import dev.hieunv.two_databases.repository.primary.LocalLedgerRepository;
import dev.hieunv.two_databases.service.InterBankTransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional(propagation = Propagation.NEVER)
public class TwoPhaseCommitTest extends BaseIntegrationTest {

    @Autowired
    private InterBankTransferService transferService;

    @Autowired
    private LocalLedgerRepository localLedgerRepository;

    @Autowired
    private ExternalLedgerRepository externalLedgerRepository;


    @BeforeEach
    void cleanup() {
        // Clean both DBs before each test
        localLedgerRepository.deleteAll();
        externalLedgerRepository.deleteAll();

    }

    @Test
    void demonstrate2PCFailure_primaryCommitsSecondaryNever() {
        System.out.println("=== 2PC FAILURE — REAL POSTGRESQL ===");
        System.out.println("Transferring $500 from Bank A → Bank B");
        System.out.println("Crash simulated after primary DB commits\n");

        Long fromAccountId = 1L;
        Long toAccountId   = 2L;
        BigDecimal amount  = new BigDecimal("500.00");

        // ── Attempt transfer — will crash mid-way ─────────────
        assertThatThrownBy(() ->
                transferService.transferBroken(
                        fromAccountId, toAccountId, amount,
                        true) // simulateCrash = true
        )
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated crash");

        System.out.println("=== STATE AFTER CRASH ===");

        // ── Check primary DB — debit IS there ─────────────────
        List<dev.hieunv.two_databases.domain.primary.LocalLedgerEntry> primaryEntries =
                localLedgerRepository.findAll();

        System.out.println("[Primary DB] entries: " + primaryEntries.size());
        primaryEntries.forEach(e ->
                System.out.println("  → " + e.getStatus()
                        + " $" + e.getAmount()));

        // ── Check secondary DB — credit is NOT there ──────────
        List<dev.hieunv.two_databases.domain.secondary.ExternalLedgerEntry> secondaryEntries =
                externalLedgerRepository.findAll();

        System.out.println("[Secondary DB] entries: " + secondaryEntries.size());
        System.out.println("Result: $500 MISSING from the system!");

        // ── Assertions — prove real inconsistency ─────────────

        // Primary committed — debit exists in real PostgreSQL
        assertThat(primaryEntries).hasSize(1);
        assertThat(primaryEntries.get(0).getStatus())
                .isEqualTo("DEBITED");
        assertThat(primaryEntries.get(0).getAmount())
                .isEqualByComparingTo("500.00");

        // Secondary never received commit — empty in real PostgreSQL
        assertThat(secondaryEntries).isEmpty();

        // This is the proof — DBs are inconsistent
        System.out.println("\nPROVEN: primary has 1 DEBITED entry,"
                + " secondary has 0 entries");
        System.out.println("$500 is gone — this is why 2PC fails!");
    }
}
