package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    // relay polls this — only PENDING events, oldest first, batch of 10
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.status = 'PENDING'
            ORDER BY e.createdAt ASC
            LIMIT 10
            """)
    List<OutboxEvent> findPendingEvents();

    List<OutboxEvent> findByAggregateTypeAndAggregateId(String aggregateType, Long aggregateId);
}
