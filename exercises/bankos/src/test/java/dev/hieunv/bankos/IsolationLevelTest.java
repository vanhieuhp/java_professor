package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.service.AccountService;
import dev.hieunv.bankos.service.ReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

@SpringBootTest
public class IsolationLevelTest {

    @Autowired
    private ReconciliationService reconciliationService;

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
    void demonstrateNonRepeatableRead() throws InterruptedException {
        System.out.println("=== NON-REPEATABLE READ DEMO ===");
        System.out.println("Initial balance: $1000.00");

        // Thread A: reconciliation job — reads balance twice
        Thread threadA = new Thread(() -> {
            try {
                reconciliationService.reconcileReadCommitted(accountId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread B: deposits $500 while reconciliation is mid-flight
        Thread threadB = new Thread(() -> {
            try {
                Thread.sleep(700); // let Thread A do its first read
                System.out.println("[Deposit]  Depositing $500...");
                accountService.deposit(accountId, new BigDecimal("500.00"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();
    }

    @Test
    void demonstrateRepeatableReadFix() throws InterruptedException {
        System.out.println("=== REPEATABLE READ FIX DEMO ===");
        System.out.println("Initial balance: $1000.00");

        Thread threadA = new Thread(() -> {
            try {
                reconciliationService.reconcileRepeatableRead(accountId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        Thread threadB = new Thread(() -> {
            try {
                Thread.sleep(700);
                System.out.println("[Deposit]  Depositing $500...");
                accountService.deposit(accountId, new BigDecimal("500.00"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        threadA.start();
        threadB.start();
        threadA.join();
        threadB.join();
    }
}
