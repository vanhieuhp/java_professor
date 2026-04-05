package dev.hieunv.two_databases.repository.primary;

import dev.hieunv.two_databases.domain.primary.LocalLedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocalLedgerRepository extends JpaRepository<LocalLedgerEntry, Long> {

    boolean existsByIdempotencyKey(String idempotencyKey);
}
