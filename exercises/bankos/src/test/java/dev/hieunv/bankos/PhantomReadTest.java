package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.PaymentRepository;
import dev.hieunv.bankos.service.ReconciliationServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PhantomReadTest {

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private ReconciliationServiceImpl reconciliationService;

    @Test
    void demonstratePhantomRead() {
        Long accountId = 1L;

        Payment p1 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .idempotencyKey("key-1")
                .status("PENDING")
                .build();

        Payment p2 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("200"))
                .idempotencyKey("key-2")
                .status("PENDING")
                .build();

        Payment p3 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("150"))
                .idempotencyKey("key-3")
                .status("PENDING")
                .build();

        // First call returns 2 payments, second call returns 3 — phantom!
        when(paymentRepository.findByAccountIdInAndStatus(List.of(accountId), "PENDING"))
                .thenReturn(List.of(p1, p2))
                .thenReturn(List.of(p1, p2, p3));

        System.out.println("=== PHANTOM READ DEMO (Mockito) ===");

        BigDecimal firstSum = reconciliationService.calculatePendingTotal(List.of(accountId));
        System.out.println("[Reconciliation] First sum:  $" + firstSum);

        // Simulate Thread B inserting — already mocked above
        System.out.println("[Thread B] Phantom payment $150 appeared...");

        // Second sum — phantom row included
        BigDecimal secondSum = reconciliationService
                .calculatePendingTotal(List.of(accountId));
        System.out.println("[Reconciliation] Second sum: $" + secondSum);

        if (firstSum.compareTo(secondSum) != 0) {
            System.out.println("[Reconciliation] 💥 PHANTOM READ DETECTED!");
        }

        // First read saw only 2 payments — $300
        assertThat(firstSum).isEqualByComparingTo("300.00");

        // Second read saw phantom — $450
        assertThat(secondSum).isEqualByComparingTo("450.00");

        // Sums differ — proves phantom read happened
        assertThat(firstSum).isNotEqualByComparingTo(secondSum);

        // Repository was called exactly twice — once per read
        verify(paymentRepository, times(2))
                .findByAccountIdInAndStatus(List.of(accountId), "PENDING");
    }

    @Test
    void demonstrateSerializableFix() {
        Long accountId = 1L;

        Payment p1 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .idempotencyKey("key-1")
                .status("PENDING")
                .build();

        Payment p2 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("200"))
                .idempotencyKey("key-2")
                .status("PENDING")
                .build();

        // SERIALIZABLE holds the snapshot — both reads return same rows
        when(paymentRepository.findByAccountIdInAndStatus(
                List.of(accountId), "PENDING"))
                .thenReturn(List.of(p1, p2))   // first read
                .thenReturn(List.of(p1, p2));  // second read — same snapshot!

        System.out.println("=== SERIALIZABLE FIX DEMO (Mockito) ===");

        BigDecimal firstSum = reconciliationService
                .calculatePendingTotal(List.of(accountId));
        System.out.println("[Reconciliation] First sum:  $" + firstSum);

        System.out.println("[Thread B] Tries to insert — blocked by SERIALIZABLE...");

        BigDecimal secondSum = reconciliationService
                .calculatePendingTotal(List.of(accountId));
        System.out.println("[Reconciliation] Second sum: $" + secondSum);

        if (firstSum.compareTo(secondSum) == 0) {
            System.out.println("[Reconciliation] ✅ Sums match — phantom prevented!");
        }

        // ── Assertions ───────────────────────────────────────

        // Both reads returned same sum — no phantom
        assertThat(firstSum).isEqualByComparingTo("300.00");
        assertThat(secondSum).isEqualByComparingTo("300.00");
        assertThat(firstSum).isEqualByComparingTo(secondSum);

        // Repository still called twice — isolation is DB-level,
        // service code doesn't change
        verify(paymentRepository, times(2))
                .findByAccountIdInAndStatus(List.of(accountId), "PENDING");
    }

    @Test
    void reconcilePendingFeesRepeatableRead_phantomPrevented() throws InterruptedException {
        Long accountId = 1L;

        Payment p1 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .idempotencyKey("key-1")
                .status("PENDING")
                .build();

        Payment p2 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("200.00"))
                .idempotencyKey("key-2")
                .status("PENDING")
                .build();

        // REPEATABLE_READ: snapshot is held — both reads return the same rows
        when(paymentRepository.findByAccountIdInAndStatus(List.of(accountId), "PENDING"))
                .thenReturn(List.of(p1, p2))
                .thenReturn(List.of(p1, p2));

        reconciliationService.reconcilePendingFeesSerializable(List.of(accountId));

        // Repository queried exactly twice (first + second read inside the method)
        verify(paymentRepository, times(2))
                .findByAccountIdInAndStatus(List.of(accountId), "PENDING");
    }

    @Test
    void reconcilePendingFeesSerializable_phantomDetected() throws InterruptedException {
        Long accountId = 1L;

        Payment p1 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("100.00"))
                .idempotencyKey("key-1")
                .status("PENDING")
                .build();

        Payment p2 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("200.00"))
                .idempotencyKey("key-2")
                .status("PENDING")
                .build();

        Payment p3 = Payment.builder()
                .accountId(accountId)
                .amount(new BigDecimal("150.00"))
                .idempotencyKey("key-3")
                .status("PENDING")
                .build();

        // Simulates DB that does NOT fully enforce SERIALIZABLE — phantom slips through
        when(paymentRepository.findByAccountIdInAndStatus(List.of(accountId), "PENDING"))
                .thenReturn(List.of(p1, p2))
                .thenReturn(List.of(p1, p2, p3));

        reconciliationService.reconcilePendingFeesRepeatableRead(List.of(accountId));

        verify(paymentRepository, times(2))
                .findByAccountIdInAndStatus(List.of(accountId), "PENDING");
    }
}
