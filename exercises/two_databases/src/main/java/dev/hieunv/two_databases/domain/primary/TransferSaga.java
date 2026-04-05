package dev.hieunv.two_databases.domain.primary;

import dev.hieunv.two_databases.common.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "transfer_sagas")
@Data
public class TransferSaga {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Long fromAccountId;

    @Column(nullable = false)
    private Long toAccountId;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, name = "status")
    private Status status;

    private String failureReason;

    @Column(unique = true)
    private String compensationKey;     // idempotency key for compensation

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public void markDebitCompleted() {
        this.status    = Status.DEBIT_COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCreditCompleted() {
        this.status    = Status.CREDIT_COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markCompensating(String reason) {
        this.status         = Status.COMPENSATING;
        this.failureReason  = reason;
        this.compensationKey = "COMPENSATE-" + this.id;
        this.updatedAt      = LocalDateTime.now();
    }

    public void markCompensated() {
        this.status    = Status.COMPENSATED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String reason) {
        this.status        = Status.FAILED;
        this.failureReason = reason;
        this.updatedAt     = LocalDateTime.now();
    }
}
