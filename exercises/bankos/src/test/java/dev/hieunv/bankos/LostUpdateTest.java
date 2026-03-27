package dev.hieunv.bankos;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.service.AccountService;
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

@SpringBootTest
public class LostUpdateTest {

    @Autowired
    private AccountService accountService;
    @Autowired
    private AccountRepository accountRepository;

    private Long accountId;

    @BeforeEach
    void setup() {
        Account account = accountRepository.save(
                new Account("Alice", new BigDecimal("1000.00"))
        );
        accountId = account.getId();
    }

    @Test
    void demonstrateLostUpdate() throws InterruptedException {
        System.out.println("=== LOST UPDATE DEMO ===");
        System.out.println("Initial balance: $1000.00");
        System.out.println("Two threads each withdraw $600 — only ONE should succeed\n");

        CountDownLatch latch = new CountDownLatch(1);
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger counter = new AtomicInteger(0);

        Thread threadA = new Thread(() -> {
            try {
                latch.await(); // wait for both threads to start together
                accountService.withdrawSafe(accountId, new BigDecimal("600.00"));
                results.add("Thread A: withdrew $600 ✅");
                counter.incrementAndGet();
            } catch (Exception e) {
                results.add("Thread A: FAILED — " + e.getMessage());
            }
        }, "Thread-A");

        Thread threadB = new Thread(() -> {
            try {
                latch.await();
                accountService.withdrawSafe(accountId, new BigDecimal("600.00"));
                results.add("Thread B: withdrew $600 ✅");
                counter.incrementAndGet();
            } catch (Exception e) {
                results.add("Thread B: FAILED — " + e.getMessage());
            }
        }, "Thread-B");

        threadA.start();
        threadB.start();
        latch.countDown(); // release both at the same time

        threadA.join();
        threadB.join();

        // Exactly one withdrawal should succeed (the other gets "Insufficient funds")
        assert counter.get() == 2;

        BigDecimal finalBalance = accountRepository.findById(accountId)
                .orElseThrow().getBalance();
        System.out.println("Final balance: $" + finalBalance);

        assert finalBalance.equals(new BigDecimal("400.00"))
                : "Expected balance $400.00 after one successful withdrawal, got $" + finalBalance;
    }
}
