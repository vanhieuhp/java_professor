package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.TransferResult;

import java.math.BigDecimal;

public interface TransferService {
    void transfer(Long fromId, Long toId, BigDecimal amount)
            throws InterruptedException;

    TransferResult transferSafe(Long fromId, Long toId, BigDecimal amount) throws InterruptedException;
}
