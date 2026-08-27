package hieunv.dev.netflixstack.payment.idempotency;

import hieunv.dev.netflixstack.common.IdempotencyStatus;
import hieunv.dev.netflixstack.common.RecoveryPoint;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;


@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IdempotencyStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_point", nullable = false, length = 64)
    private RecoveryPoint recoveryPoint;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "stripe_idempotency_key")
    private String stripeIdempotencyKey;

    @Column(name = "stripe_charge_id")
    private String stripeChargeId;

    @Column(name = "payment_id")
    private Long paymentId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response")
    private String response;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * A fresh key, locked by the caller and already carrying the Stripe key it
     * will charge under. Generating that key here - before any charge is
     * attempted - is what lets a retry after an unknown outcome ask Stripe about
     * the same charge instead of creating a new one.
     */
    public static IdempotencyRecord started(Long userId, String idempotencyKey, String requestHash) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        IdempotencyRecord record = new IdempotencyRecord();
        record.userId = userId;
        record.idempotencyKey = idempotencyKey;
        record.requestHash = requestHash;
        record.status = IdempotencyStatus.PROCESSING;
        record.recoveryPoint = RecoveryPoint.STARTED;
        record.lockedAt = now;
        record.stripeIdempotencyKey = "stripe_" + UUID.randomUUID();
        record.createdAt = now;
        record.updatedAt = now;
        return record;
    }

    public boolean isTerminal() {
        return status != IdempotencyStatus.PROCESSING;
    }

    /** True while another worker's lease is still valid, so this request must stand off. */
    public boolean isLeaseHeld(Duration lease) {
        return lockedAt != null
                && lockedAt.isAfter(LocalDateTime.now(ZoneOffset.UTC).minus(lease));
    }

    public void takeLease() {
        this.lockedAt = LocalDateTime.now(ZoneOffset.UTC);
        touch();
    }

    public void releaseLease() {
        this.lockedAt = null;
        touch();
    }

    public void advanceTo(RecoveryPoint next) {
        this.recoveryPoint = next;
        takeLease(); // each phase renews the lease, so slow-but-alive never looks dead
    }

    /** Terminal states keep no lease - there is nothing left to resume. */
    public void finish(IdempotencyStatus finalStatus, String responseJson) {
        this.status = finalStatus;
        this.recoveryPoint = RecoveryPoint.FINISHED;
        this.response = responseJson;
        this.lockedAt = null;
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
