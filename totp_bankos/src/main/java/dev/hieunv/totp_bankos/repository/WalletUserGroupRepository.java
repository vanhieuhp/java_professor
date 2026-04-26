package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.WalletUserGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WalletUserGroupRepository extends JpaRepository<WalletUserGroup, Long> {

    // which group is this user in, inside this wallet?
    Optional<WalletUserGroup> findByWalletIdAndUserIdAndIsActiveTrue(
            Long walletId, Long userId
    );

    // all users in a group
    List<WalletUserGroup> findByGroupIdAndIsActiveTrue(Long groupId);

    boolean existsByWalletIdAndUserIdAndIsActiveTrue(Long walletId, Long userId);
}