package dev.hieunv.two_databases.domain.secondary;

import dev.hieunv.two_databases.common.LedgerStatus;
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

@Entity
@Table(name = "external_ledger_entries")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExternalLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "amount")
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private LedgerStatus status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(unique = true, name = "idempotency_key")
    private String idempotencyKey;

    public ExternalLedgerEntry(Long accountId, BigDecimal amount, LedgerStatus status) {
        this.accountId = accountId;
        this.amount    = amount;
        this.status    = status;
        this.createdAt = LocalDateTime.now();
    }
}
