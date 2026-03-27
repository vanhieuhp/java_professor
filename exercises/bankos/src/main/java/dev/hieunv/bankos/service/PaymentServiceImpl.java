package dev.hieunv.bankos.service;

import dev.hieunv.bankos.repository.AccountRepository;
import dev.hieunv.bankos.repository.IdempotencyKeyRepository;
import dev.hieunv.bankos.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final AccountRepository accountRepository;
}
