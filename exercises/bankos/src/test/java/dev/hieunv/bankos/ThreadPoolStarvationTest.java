package dev.hieunv.bankos;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import dev.hieunv.bankos.service.PaymentGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ThreadPoolStarvationTest {

    @Autowired
    private PaymentGatewayService gatewayService;

    @Test
    void demonstrateStarvation_singleSharedPool()
            throws InterruptedException {

        System.out.println("=== THREAD POOL STARVATION DEMO ===");
        System.out.println("4 threads, 4 slow gateway calls + 4 fast validations\n");

        // Shared pool — only 4 threads, same as core count
        ExecutorService sharedPool = new ThreadPoolExecutor(
                4, 4, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>(),
                new ThreadFactoryBuilder()
                        .setNameFormat("shared-%d").build());

        List<CompletableFuture<?>> futures = new ArrayList<>();
        List<Long> validationTimes =
                Collections.synchronizedList(new ArrayList<>());

        // Submit 4 slow gateway calls — occupy all 4 threads
        for (int i = 0; i < 4; i++) {
            final long accountId = i + 1;
            futures.add(gatewayService.callGatewayBroken(accountId, new BigDecimal("100"), sharedPool));
        }

        // Small pause — let gateway calls grab all threads
        Thread.sleep(100);

        // Submit 4 fast validations — should be fast but will starve
        System.out.println("\n[Test] Submitting fast validations"
                + " — all threads occupied by gateway calls!\n");

        for (int i = 0; i < 4; i++) {
            final long accountId = i + 1;
            long submitTime = System.currentTimeMillis();

            futures.add(
                    gatewayService.validateBroken(accountId, sharedPool)
                            .thenApply(result -> {
                                long waitTime = System.currentTimeMillis() - submitTime;
                                validationTimes.add(waitTime);
                                System.out.println("[Validation] Waited " + waitTime + "ms to start!");
                                return result;
                            })
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        System.out.println("\n=== VALIDATION WAIT TIMES ===");
        validationTimes.forEach(t -> System.out.println("  waited: " + t + "ms"));

        // ── Assertions ────────────────────────────────────────

        // All validations were starved — waited > 1 second
        // despite being near-instant CPU work
        assertThat(validationTimes).hasSize(4);
        assertThat(validationTimes).allSatisfy(waitTime ->
                assertThat(waitTime)
                        .as("Validation starved — waited too long")
                        .isGreaterThan(1000L));

        System.out.println("\n💥 STARVATION PROVEN — fast tasks waited"
                + " >1s because slow tasks occupied all threads!");

        sharedPool.shutdown();
    }

    @Test
    void demonstrateFix_separatePools() throws InterruptedException {

        System.out.println("=== SEPARATE POOLS FIX DEMO ===\n");

        List<CompletableFuture<?>> futures = new ArrayList<>();
        List<Long> validationTimes = Collections.synchronizedList(new ArrayList<>());

        // Submit 4 slow gateway calls — go to IO pool
        for (int i = 0; i < 4; i++) {
            futures.add(gatewayService.callGatewayAsync((long) i + 1, new BigDecimal("100")));
        }

        Thread.sleep(100);

        // Submit 4 fast validations — go to CPU pool (not blocked!)
        System.out.println("\n[Test] Submitting fast validations" + " — CPU pool is free!\n");

        for (int i = 0; i < 4; i++) {
            long submitTime = System.currentTimeMillis();
            final long accountId = i + 1;

            futures.add(
                    gatewayService.validatePaymentAsync(accountId, new BigDecimal("100"))
                            .thenApply(result -> {
                                long waitTime = System.currentTimeMillis()
                                        - submitTime;
                                validationTimes.add(waitTime);
                                System.out.println("[Validation] Completed in "
                                        + waitTime + "ms ✅");
                                return result;
                            })
            );
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        System.out.println("\n=== VALIDATION WAIT TIMES ===");
        validationTimes.forEach(t -> System.out.println("  completed in: " + t + "ms"));

        // ── Assertions ────────────────────────────────────────

        // Validations completed almost instantly — not starved
        assertThat(validationTimes).hasSize(4);
        assertThat(validationTimes).allSatisfy(waitTime ->
                assertThat(waitTime)
                        .as("Validation should complete quickly"
                                + " on separate pool")
                        .isLessThan(500L));

        System.out.println("\n✅ STARVATION FIXED — fast tasks completed"
                + " in <500ms while gateway calls still running!");
    }

    @Test
    void demonstrateVirtualThreads_java21() throws InterruptedException {

        System.out.println("=== VIRTUAL THREADS — JAVA 21 ===\n");
        System.out.println("Submitting 50 concurrent gateway calls...");

        long start = System.currentTimeMillis();
        List<CompletableFuture<String>> futures = new ArrayList<>();

        // Virtual threads handle 50 concurrent I/O tasks trivially
        // Each virtual thread is ~1KB vs ~1MB for platform threads
        for (int i = 0; i < 50; i++) {
            final long accountId = i + 1;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    Thread.sleep(200); // simulate I/O
                    return "OK-" + accountId;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return "FAILED";
                }
            }, gatewayService.getVirtualPool()));
        }

        List<String> results = futures.stream()
                .map(CompletableFuture::join)
                .toList();

        long elapsed = System.currentTimeMillis() - start;

        System.out.println("50 concurrent calls completed in: "
                + elapsed + "ms");
        System.out.println("Expected: ~200ms (all run in parallel)");

        // ── Assertions ────────────────────────────────────────

        // All 50 completed
        assertThat(results).hasSize(50);
        assertThat(results).allMatch(r -> r.startsWith("OK-"));

        // Completed in roughly 200ms — all truly parallel
        assertThat(elapsed)
                .as("Virtual threads should handle 50 concurrent"
                        + " I/O tasks in ~200ms")
                .isLessThan(1000L);

        System.out.println("\n✅ Virtual threads: 50 concurrent I/O tasks"
                + " in " + elapsed + "ms with zero pool management!");
    }

}
