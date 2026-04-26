package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountWalletRepository extends JpaRepository<AccountWallet, Long> {

    Optional<AccountWallet> findByCode(String code);

    // all wallets belonging to a CIF
    List<AccountWallet> findByCifIdAndIsActiveTrue(Long cifId);

    boolean existsByCode(String code);
}