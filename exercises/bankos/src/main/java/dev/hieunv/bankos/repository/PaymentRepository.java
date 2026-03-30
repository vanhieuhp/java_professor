package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    // Used by reconciliation service
    List<Payment> findByAccountIdInAndStatus(List<Long> accountIds, String status);
}
