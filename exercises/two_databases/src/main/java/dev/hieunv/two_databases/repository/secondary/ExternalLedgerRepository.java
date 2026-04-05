package dev.hieunv.two_databases.repository.secondary;

import dev.hieunv.two_databases.domain.secondary.ExternalLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExternalLedgerRepository extends JpaRepository<ExternalLedgerEntry, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
