package dev.hieunv.two_databases.domain.primary;

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


@Data
@Builder
@Entity
@Table(name = "local_ledge_entries")
@NoArgsConstructor
@AllArgsConstructor
public class LocalLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long accountId;
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    private LedgerStatus status;

    @Column(unique = true)
    private String idempotencyKey;

    private LocalDateTime createdAt;
}
