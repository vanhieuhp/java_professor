package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.AccountResponse;
import dev.hieunv.bankos.dto.CreateAccountRequest;
import dev.hieunv.bankos.model.Account;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    void slowDeposit(Long accountId, BigDecimal amount) throws InterruptedException;

    BigDecimal readBalanceDirty(Long accountId);

    BigDecimal readBalanceSafe(Long accountId);

    void withdraw(Long accountId, BigDecimal amount) throws InterruptedException;

    void withdrawSafe(Long accountId, BigDecimal amount) throws InterruptedException;

    void deposit(Long accountId, BigDecimal amount);


    @Transactional
    Account createAccount(CreateAccountRequest request);

    @Transactional(readOnly = true)
    List<AccountResponse> findAll();
}
