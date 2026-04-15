package dev.hieunv.bankos.service;

import jakarta.transaction.Transactional;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;

public interface FlashSaleService {

    @Transactional
    void decrementStock(Long productId, int quantity);

    @Retryable(
            retryFor = ObjectOptimisticLockingFailureException.class,
            maxAttempts = 3,
            backoff = @Backoff(delay = 100)
    )
    @Transactional
    void decrementStockSafe(Long productId, int quantity);

    @Recover
    void recover(ObjectOptimisticLockingFailureException e, Long productId, int quantity);
}
