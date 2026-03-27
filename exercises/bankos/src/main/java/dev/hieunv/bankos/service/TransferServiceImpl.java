package dev.hieunv.bankos.service;

import dev.hieunv.bankos.dto.TransferResult;
import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.repository.AccountRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Service
@Slf4j
public class TransferServiceImpl implements TransferService {

    private final AccountRepository accountRepository;

    @Transactional
    @Override
    public void transfer(Long fromId, Long toId, BigDecimal amount)
            throws InterruptedException {
        System.out.println(Thread.currentThread().getName()
                + " locking Account " + fromId + "...");

        Account from = accountRepository.findByIdWithLock(fromId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + fromId));

        System.out.println(Thread.currentThread().getName()
                + " locked Account " + fromId
                + " → now trying Account " + toId + "...");

        // Pause here so the other thread has time to grab its first lock
        Thread.sleep(300);

        Account to = accountRepository.findByIdWithLock(toId)
                .orElseThrow(() -> new EntityNotFoundException("Account not found: " + toId));

        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        System.out.println(Thread.currentThread().getName()
                + " transferred $" + amount
                + " from Account " + fromId
                + " → Account " + toId);
    }

    @Transactional
    @Override
    public TransferResult transferSafe(Long fromId, Long toId, BigDecimal amount){
        validateTransfer(fromId, toId, amount);

        Long firstId  = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        Account first = accountRepository.findByIdWithLock(firstId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + firstId));
        Account second = accountRepository.findByIdWithLock(secondId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + secondId));

        try {
            Thread.sleep(6000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        Account from = fromId.equals(firstId) ? first : second;
        Account to   = toId.equals(firstId)   ? first : second;

        if (from.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    "Insufficient funds! Available: $" + from.getBalance()
                            + " Requested: $" + amount);
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        return new TransferResult(fromId, toId, amount,
                from.getBalance(), to.getBalance());
    }

    private void validateTransfer(Long fromId, Long toId, BigDecimal amount) {
        if (fromId.equals(toId)) {
            throw new IllegalArgumentException("Cannot transfer to the same account!");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be positive!");
        }
    }
}
