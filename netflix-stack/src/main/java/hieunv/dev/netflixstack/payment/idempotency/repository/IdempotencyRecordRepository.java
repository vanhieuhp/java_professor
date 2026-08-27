package hieunv.dev.netflixstack.payment.idempotency.repository;

import hieunv.dev.netflixstack.payment.idempotency.entity.IdempotencyRecord;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRecord r where r.userId = :userId and r.idempotencyKey = :key")
    Optional<IdempotencyRecord> lockByUserAndKey(@Param("userId") Long userId, @Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from IdempotencyRecord r where r.id = :id")
    Optional<IdempotencyRecord> lockById(@Param("id") Long id);
}
