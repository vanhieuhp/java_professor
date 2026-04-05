package dev.hieunv.two_databases.service;

import dev.hieunv.two_databases.domain.primary.OutboxEvent;

import java.math.BigDecimal;

public interface InterBankTransferService {
    void transferBroken(Long fromAccountId,
                        Long toAccountId,
                        BigDecimal amount,
                        boolean simulateCrash);

    String transferWithOutbox(Long fromAccountId,
                              Long toAccountId,
                              BigDecimal amount);

    void relayPendingEvents();

    void deliverToSecondaryDb(OutboxEvent event);
}
