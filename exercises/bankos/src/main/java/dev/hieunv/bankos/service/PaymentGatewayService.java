package dev.hieunv.bankos.service;

import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

public interface PaymentGatewayService {
    CompletableFuture<Boolean> validatePaymentAsync(
            Long accountId, BigDecimal amount);

    CompletableFuture<String> callGatewayAsync(
            Long accountId, BigDecimal amount);

    CompletableFuture<String> callGatewayBroken(
            Long accountId, BigDecimal amount,
            ExecutorService sharedPool);

    CompletableFuture<Boolean> validateBroken(
            Long accountId, ExecutorService sharedPool);

    ExecutorService getVirtualPool();
}
