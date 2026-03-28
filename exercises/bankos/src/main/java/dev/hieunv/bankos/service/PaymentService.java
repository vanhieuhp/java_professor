package dev.hieunv.bankos.service;

import dev.hieunv.bankos.model.Payment;
import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.repository.IdempotencyKeyRepository;
import dev.hieunv.bankos.repository.PaymentRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;

public interface PaymentService {

    Payment processPayment(Long accountId, BigDecimal amount);


    @Transactional
    Payment processPaymentIdempotent(
            Long accountId,
            BigDecimal amount,
            String idempotencyKey);
}
