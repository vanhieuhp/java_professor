package dev.hieunv.bankos.service;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface ReconciliationService {

    void reconcileReadCommitted(Long accountId) throws InterruptedException;

    void reconcileRepeatableRead(Long accountId) throws InterruptedException;

    void reconcilePendingFeesRepeatableRead(List<Long> accountIds) throws InterruptedException;

    @Transactional(isolation = Isolation.SERIALIZABLE)
    void reconcilePendingFeesSerializable(List<Long> accountIds) throws InterruptedException;

    // Shared helper — sums all PENDING payments for given accounts
    BigDecimal calculatePendingTotal(List<Long> accountIds);
}
