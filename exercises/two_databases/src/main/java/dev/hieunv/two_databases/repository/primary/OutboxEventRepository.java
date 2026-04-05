package dev.hieunv.two_databases.repository.primary;

import dev.hieunv.two_databases.domain.primary.OutboxEvent;
import dev.hieunv.two_databases.common.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    // Relay polls this — only PENDING events
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // Consumer checks this for idempotency
    Optional<OutboxEvent> findByIdempotencyKey(String idempotencyKey);
}
