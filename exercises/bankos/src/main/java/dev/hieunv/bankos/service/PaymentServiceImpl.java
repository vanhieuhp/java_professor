package dev.hieunv.bankos.service;

import dev.hieunv.bankos.model.Account;
import dev.hieunv.bankos.model.IdempotencyKey;
import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.repository.IdempotencyKeyRepository;
import dev.hieunv.bankos.repository.PaymentRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AccountRepository accountRepository;

    @Transactional
    @Override
    public Payment processPayment(Long accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + accountId));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));
        Payment payment = paymentRepository.save(new Payment(accountId, amount));

        System.out.println("[Payment] Processed $" + amount
                + " from Account " + accountId
                + " → Payment ID: " + payment.getId()
                + " → balance: $" + account.getBalance());

        return payment;
    }

    @Transactional
    @Override
    public Payment processPaymentIdempotent(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey) {

        // 1. Check if we already processed this exact request
        Optional<Payment> existing =
                paymentRepository.findByIdempotencyKey(idempotencyKey);

        if (existing.isPresent()) {
            System.out.println("[Payment] Duplicate detected for key: "
                    + idempotencyKey
                    + " → returning cached Payment ID: "
                    + existing.get().getId());
            return existing.get(); // return original result — do NOT reprocess
        }

        // 2. New request — process it
        Account account = accountRepository.findByIdWithLock(accountId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Account not found: " + accountId));

        if (account.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds!");
        }

        account.setBalance(account.getBalance().subtract(amount));

        // 3. Save payment + idempotency key in ONE transaction
        //    If this crashes halfway, both roll back together — atomic!
        Payment payment = paymentRepository.save(
                new Payment(accountId, amount, idempotencyKey));

        idempotencyKeyRepository.save(
                new IdempotencyKey(idempotencyKey, payment.getId()));

        System.out.println("[Payment] New payment processed for key: "
                + idempotencyKey
                + " → Payment ID: " + payment.getId()
                + " → balance: $" + account.getBalance());

        return payment;
    }

}
