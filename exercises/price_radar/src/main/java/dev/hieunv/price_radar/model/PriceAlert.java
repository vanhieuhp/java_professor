package dev.hieunv.price_radar.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "price_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PriceAlert {

    @Id
    private String alertId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private double threshold;

    @Builder.Default
    private boolean active = true;

    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (alertId == null) alertId = UUID.randomUUID().toString();
        if (createdAt == null) createdAt = Instant.now();
    }
}
