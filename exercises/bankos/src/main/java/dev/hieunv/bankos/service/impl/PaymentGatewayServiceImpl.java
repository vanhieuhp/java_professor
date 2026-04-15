package dev.hieunv.bankos.service.impl;

import dev.hieunv.bankos.service.PaymentGatewayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Service
@Slf4j
public class PaymentGatewayServiceImpl implements PaymentGatewayService {


    @Autowired
    @Qualifier("cpuTaskExecutor")
    private ExecutorService cpuPool;

    @Autowired
    @Qualifier("ioTaskExecutor")
    private ExecutorService ioPool;

    @Autowired
    @Qualifier("virtualThreadExecutor")
    private ExecutorService virtualPool;

    @Override
    public CompletableFuture<Boolean> validatePaymentAsync(Long accountId, BigDecimal amount) {

        return CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("[Validation] " + thread
                    + " validating account=" + accountId);

            // Simulate CPU work — no I/O
            boolean valid = accountId > 0
                    && amount.compareTo(BigDecimal.ZERO) > 0;

            System.out.println("[Validation] " + thread
                    + " done → " + valid);
            return valid;

        }, cpuPool);  // ← CPU pool
    }

    @Override
    public CompletableFuture<String> callGatewayAsync(Long accountId, BigDecimal amount) {

        return CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("[Gateway] " + thread + " calling external gateway...");

            try {
                // Simulate slow network I/O — blocks thread
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted");
            }

            System.out.println("[Gateway] " + thread + " response received");
            return "GATEWAY_OK_" + accountId;

        }, ioPool);   // ← IO pool
    }

    @Override
    public CompletableFuture<String> callGatewayBroken(
            Long accountId, BigDecimal amount,
            ExecutorService sharedPool) {

        return CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("[Broken] " + thread
                    + " calling gateway (shared pool)...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "OK";
        }, sharedPool);
    }


    @Override
    public CompletableFuture<Boolean> validateBroken(
            Long accountId, ExecutorService sharedPool) {

        return CompletableFuture.supplyAsync(() -> {
            String thread = Thread.currentThread().getName();
            System.out.println("[Broken] " + thread
                    + " validating (shared pool)...");
            return true;
        }, sharedPool);
    }

    @Override
    public ExecutorService getVirtualPool() {
        return virtualPool;
    }
}
