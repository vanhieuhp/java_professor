package dev.hieunv.bankos.service;

import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

public interface ReconciliationService {

    void reconcileReadCommitted(Long accountId) throws InterruptedException;

    // FIXED — REPEATABLE_READ locks the snapshot
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    void reconcileRepeatableRead(Long accountId) throws InterruptedException;
}
