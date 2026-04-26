package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.WalletUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletUserRepository extends JpaRepository<WalletUser, Long> {

    // check if user is a member of this wallet
    Optional<WalletUser> findByWalletIdAndUserId(Long walletId, Long userId);

    // all active wallets a user belongs to
    List<WalletUser> findByUserIdAndIsActiveTrue(Long userId);

    // all active users in a wallet
    List<WalletUser> findByWalletIdAndIsActiveTrue(Long walletId);

    boolean existsByWalletIdAndUserIdAndIsActiveTrue(Long walletId, Long userId);

    // wallet IDs the user has access to — used when building login response
    @Query("""
        SELECT wu.walletId
        FROM WalletUser wu
        WHERE wu.userId = :userId
          AND wu.isActive = true
        """)
    List<Long> findWalletIdsByUserId(@Param("userId") Long userId);
}