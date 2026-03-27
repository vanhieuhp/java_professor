package dev.hieunv.bankos.service;

import dev.hieunv.bankos.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Slf4j
@Service
public class ReconciliationServiceImpl implements ReconciliationService{

    private final AccountRepository accountRepository;

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reconcileReadCommitted(Long accountId) throws InterruptedException {
        System.out.println("\n[Reconciliation] Starting with READ_COMMITTED...");

        BigDecimal firstRead = accountRepository.findById(accountId)
                .get().getBalance();
        System.out.println("[Reconciliation] First read:  $" + firstRead);

        // Simulate processing time — another transaction commits here
        Thread.sleep(1500);

        // Second read — should be same value to verify consistency
        BigDecimal secondRead = accountRepository.findById(accountId)
                .get().getBalance();
        System.out.println("[Reconciliation] Second read: $" + secondRead);

        if (firstRead.compareTo(secondRead) != 0) {
            System.out.println("[Reconciliation] INCONSISTENT REPORT DETECTED!");
            System.out.println("[Reconciliation] Balance changed from $"
                    + firstRead + " to $" + secondRead
                    + " mid-transaction!");
        } else {
            System.out.println("[Reconciliation] Report consistent.");
        }
    }

    // FIXED — REPEATABLE_READ locks the snapshot
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Override
    public void reconcileRepeatableRead(Long accountId) throws InterruptedException {
        System.out.println("\n[Reconciliation] Starting with REPEATABLE_READ...");

        BigDecimal firstRead = accountRepository.findById(accountId)
                .get().getBalance();
        System.out.println("[Reconciliation] First read:  $" + firstRead);

        Thread.sleep(1500);

        BigDecimal secondRead = accountRepository.findById(accountId)
                .get().getBalance();
        System.out.println("[Reconciliation] Second read: $" + secondRead);

        if (firstRead.compareTo(secondRead) != 0) {
            System.out.println("[Reconciliation] INCONSISTENT REPORT DETECTED!");
        } else {
            System.out.println("[Reconciliation] Report consistent — snapshot held.");
        }
    }
}
