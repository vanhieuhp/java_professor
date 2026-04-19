package dev.hieunv.bankos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // which aggregate this event belongs to
    private String aggregateType;   // e.g. "Payment"
    private Long aggregateId;       // e.g. paymentId

    // event type — consumers use this to decide what to do
    private String eventType;       // e.g. "PAYMENT_PROCESSED"

    // JSON payload — serialized event data
    @Column(columnDefinition = "TEXT")
    private String payload;

    // relay tracks this
    private String status;          // PENDING → PUBLISHED → FAILED

    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    // how many times relay has tried to publish
    private int retryCount;
}
