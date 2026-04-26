package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {

    // all active groups in a wallet
    List<Group> findByWalletIdAndIsActiveTrue(Long walletId);

    // find group by name inside a wallet (unique per wallet)
    Optional<Group> findByWalletIdAndName(Long walletId, String name);

    boolean existsByWalletIdAndName(Long walletId, String name);
}