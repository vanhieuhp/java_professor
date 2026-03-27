package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class DeadlockTest {

    @Autowired
    private TransferService transferService;
    @Autowired private AccountRepository accountRepository;

    private Long accountAId;
    private Long accountBId;

    @BeforeEach
    void setup() {
        Account a = accountRepository.save(
                new Account("Alice", new BigDecimal("1000.00")));
        Account b = accountRepository.save(
                new Account("Bob", new BigDecimal("1000.00")));
        accountAId = a.getId();
        accountBId = b.getId();
    }

    @Test
    void demonstrateDeadlock() throws InterruptedException {
        System.out.println("=== DEADLOCK DEMO ===");
        System.out.println("Account A id=" + accountAId + " balance=$1000");
        System.out.println("Account B id=" + accountBId + " balance=$1000");
        System.out.println("Thread A: transfers A → B");
        System.out.println("Thread B: transfers B → A\n");

        List<String> results = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            try {
                latch.await();
                transferService.transfer(accountAId, accountBId,
                        new BigDecimal("100.00"));
                results.add("Thread A: transfer succeeded ✅");
            } catch (Exception e) {
                results.add("Thread A: FAILED — " + e.getMessage());
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                latch.await();
                transferService.transfer(accountBId, accountAId,
                        new BigDecimal("100.00"));
                results.add("Thread B: transfer succeeded ✅");
            } catch (Exception e) {
                results.add("Thread B: FAILED — " + e.getMessage());
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();
        latch.countDown(); // release both at the same time

        threadA.join();
        threadB.join();

        System.out.println("\n=== RESULTS ===");
        results.forEach(System.out::println);

        BigDecimal balanceA = accountRepository.findById(accountAId).get().getBalance();
        BigDecimal balanceB = accountRepository.findById(accountBId).get().getBalance();
        System.out.println("\nFinal balance A: $" + balanceA);
        System.out.println("Final balance B: $" + balanceB);
        System.out.println("Total money: $" + balanceA.add(balanceB)
                + " (should always be $2000)");

        // 1. total money never changes — no matter who wins
        assertThat(balanceA.add(balanceB))
                .isEqualByComparingTo("2000.00");

        // 2. exactly one succeeded, one failed
        long successCount = results.stream()
                .filter(r -> r.contains("succeeded"))
                .count();
        long failCount = results.stream()
                .filter(r -> r.contains("FAILED"))
                .count();
        assertThat(successCount).isEqualTo(1);
        assertThat(failCount).isEqualTo(1);

        // 3. balances reflect exactly one transfer of $100
        // (hint: either A=1100,B=900 OR A=900,B=1100 depending on who won)
        assertThat(balanceA).satisfiesAnyOf(
                b -> assertThat(b).isEqualByComparingTo("900.00"),
                b -> assertThat(b).isEqualByComparingTo("1100.00")
        );
        assertThat(balanceB).satisfiesAnyOf(
                b -> assertThat(b).isEqualByComparingTo("900.00"),
                b -> assertThat(b).isEqualByComparingTo("1100.00")
        );
    }

    @Test
    void demonstrateDeadlockFixed() throws InterruptedException {
        AtomicInteger successCount = new AtomicInteger(0);
        List<String> failures      = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch        = new CountDownLatch(1);

        Thread threadA = new Thread(() -> {
            try {
                latch.await();
                transferService.transferSafe(accountAId, accountBId, new BigDecimal("100.00"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failures.add("Thread A: " + e.getMessage());
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                latch.await();
                transferService.transferSafe(accountBId, accountAId, new BigDecimal("200.00"));
                successCount.incrementAndGet();
            } catch (Exception e) {
                failures.add("Thread B: " + e.getMessage());
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();
        latch.countDown();
        threadA.join();
        threadB.join();

        BigDecimal balanceA = accountRepository.findById(accountAId).get().getBalance();
        BigDecimal balanceB = accountRepository.findById(accountBId).get().getBalance();

        // ── Assertions ───────────────────────────────────────────

        // Both transfers succeeded — no deadlock
        assertThat(successCount.get()).isEqualTo(2);
        assertThat(failures).isEmpty();

        // Total money conserved
        assertThat(balanceA.add(balanceB)).isEqualByComparingTo("2000.00");

        // Net effect is zero — both balances back to $1000
        assertThat(balanceA).isEqualByComparingTo("1100.00");
        assertThat(balanceB).isEqualByComparingTo("900.00");
    }
}
