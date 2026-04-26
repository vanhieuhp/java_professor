package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.CashinRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CashinRequestRepository extends JpaRepository<CashinRequest, Long> {

    Page<CashinRequest> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    Page<CashinRequest> findByWalletIdAndCreatedByOrderByCreatedAtDesc(
            Long walletId, Long createdBy, Pageable pageable);

    Page<CashinRequest> findByWalletIdAndStatusOrderByCreatedAtDesc(
            Long walletId, CashinRequest.Status status, Pageable pageable);
}