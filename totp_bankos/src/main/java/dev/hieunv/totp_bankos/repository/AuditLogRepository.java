package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    // paginated audit log per user
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    // paginated audit log per wallet
    Page<AuditLog> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    // denied access attempts in a time range — used for security monitoring
    @Query("""
        SELECT a FROM AuditLog a
        WHERE a.walletId = :walletId
          AND a.granted  = false
          AND a.createdAt BETWEEN :from AND :to
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> findDeniedByWalletIdAndTimeRange(
            @Param("walletId") Long walletId,
            @Param("from")     LocalDateTime from,
            @Param("to")       LocalDateTime to,
            Pageable pageable
    );
}