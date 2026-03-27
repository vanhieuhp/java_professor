package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.service.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

@SpringBootTest
public class DirtyReadTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(new Account("Alice", new BigDecimal("1000.00")));
        accountId = account.getId();
    }

    @Test
    void demonstrateDirtyRead() throws InterruptedException {
        System.out.println("=== DIRTY READ DEMO ===");
        System.out.println("Initial balance: " + accountRepository.findById(accountId).get().getBalance());

        // Thread A: starts a deposit of $500 but sleeps 2 sec before committing
        Thread threadA = new Thread(() -> {
            try {
                System.out.println("[Thread A] Starting slow deposit of $500...");
                accountService.slowDeposit(accountId, new BigDecimal("500.00"));
                System.out.println("[Thread A] Deposit committed!");
            } catch (Exception e) {
                System.out.println("[Thread A] ROLLED BACK! " + e.getMessage());
            }
        });

        // Thread B: reads balance 500ms into Thread A's uncommitted transaction
        Thread threadB = new Thread(() -> {
            try {
                Thread.sleep(500); // wait for Thread A to write but not commit

                // ❌ DIRTY READ — reads Thread A's uncommitted $1500
                BigDecimal dirtyBalance = accountService.readBalanceDirty(accountId);
                System.out.println("[Thread B - DIRTY READ]  Balance seen: $" + dirtyBalance);

                // ✅ SAFE READ — waits for commit, sees real $1000
                BigDecimal safeBalance = accountService.readBalanceSafe(accountId);
                System.out.println("[Thread B - SAFE READ]   Balance seen: $" + safeBalance);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();

        BigDecimal finalBalance = accountRepository.findById(accountId).get().getBalance();
        System.out.println("Final committed balance: $" + finalBalance);
    }
}
