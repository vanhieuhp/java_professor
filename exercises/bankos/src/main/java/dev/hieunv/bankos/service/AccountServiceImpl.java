package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.AccountResponse;
import dev.hieunv.bankos.dto.CreateAccountRequest;
import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;

    // Simulates a long-running transfer that hasn't committed yet
    @Transactional
    @Override
    public void slowDeposit(Long accountId, BigDecimal amount) throws InterruptedException {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(account.getBalance().add(amount));
        // Simulate slow processing — not committed yet!
        Thread.sleep(2000);
        // After sleep: either commits or rolls back
    }

    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    @Override
    public BigDecimal readBalanceDirty(Long accountId) {
        return accountRepository.findByIdUncommitted(accountId)
                .map(Account::getBalance)
                .orElseThrow();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Override
    public BigDecimal readBalanceSafe(Long accountId) {
        return accountRepository.findById(accountId)
                .map(Account::getBalance)
                .orElseThrow();
    }

    @Transactional
    @Override
    public void withdraw(Long accountId, BigDecimal amount) throws InterruptedException {
        Account account = accountRepository.findById(accountId).orElseThrow();

        // Simulate real-world processing delay (e.g. fraud check)
        Thread.sleep(300);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));
        System.out.println(Thread.currentThread().getName()
                + " withdrew $" + amount
                + " → new balance: $" + account.getBalance());
    }

    @Transactional
    @Override
    public void withdrawSafe(Long accountId, BigDecimal amount) throws InterruptedException {
        // SELECT ... FOR UPDATE — second thread BLOCKS here until first commits
        Account account = accountRepository.findByIdWithLock(accountId).orElseThrow();
        Thread.sleep(300);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));
        System.out.println(Thread.currentThread().getName()
                + " withdrew $" + amount
                + " → new balance: $" + account.getBalance());
    }

    @Override
    public void deposit(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findById(accountId).orElseThrow();
        account.setBalance(account.getBalance().add(amount));
        System.out.println("[Deposit] Committed $" + amount
                + " → new balance: $" + account.getBalance());
    }

    @Transactional
    @Override
    public Account createAccount(CreateAccountRequest request) {
        return accountRepository.save(
                new Account(request.getOwner(), request.getInitialBalance())
        );
    }

    @Transactional(readOnly = true)
    @Override
    public List<AccountResponse> findAll() {
        return accountRepository.findAll()
                .stream()
                .map(AccountResponse::from)
                .toList();
    }
}
