package dev.hieunv.totp_bankos.repository;

import dev.hieunv.totp_bankos.domain.TransferRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TransferRequestRepository extends JpaRepository<TransferRequest, Long> {

    // all requests in a wallet, newest first
    Page<TransferRequest> findByWalletIdOrderByCreatedAtDesc(Long walletId, Pageable pageable);

    // maker views only their own requests
    Page<TransferRequest> findByWalletIdAndCreatedByOrderByCreatedAtDesc(
            Long walletId, Long createdBy, Pageable pageable);

    // checker views only PENDING requests
    Page<TransferRequest> findByWalletIdAndStatusOrderByCreatedAtDesc(
            Long walletId, TransferRequest.Status status, Pageable pageable);
}