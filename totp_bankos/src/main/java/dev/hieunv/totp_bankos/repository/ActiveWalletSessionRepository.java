package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.ActiveWalletSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ActiveWalletSessionRepository extends JpaRepository<ActiveWalletSession, Long> {

    Optional<ActiveWalletSession> findByUserId(Long userId);

    Optional<ActiveWalletSession> findByJwtTokenId(String jwtTokenId);

    // delete session on logout or wallet switch
    @Modifying
    @Query("DELETE FROM ActiveWalletSession s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") Long userId);

    boolean existsByUserId(Long userId);
}