package dev.hieunv.bankos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "idempotency_keys",
        uniqueConstraints = @UniqueConstraint(columnNames = "idempotency_key"))
public class IdempotencyKey {


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, unique = true)
    private String idempotencyKey;

    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public IdempotencyKey(String idempotencyKey, Long paymentId) {
        this.idempotencyKey = idempotencyKey;
        this.paymentId = paymentId;
    }
}
