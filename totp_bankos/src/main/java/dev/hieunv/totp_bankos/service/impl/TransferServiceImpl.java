package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import dev.hieunv.totp_bankos.domain.TransferRequest;
import dev.hieunv.totp_bankos.dto.request.CreateTransferRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.dto.response.TransferRequestResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.ForbiddenException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.TransferRequestMapper;
import dev.hieunv.totp_bankos.repository.AccountWalletRepository;
import dev.hieunv.totp_bankos.repository.TransferRequestRepository;
import dev.hieunv.totp_bankos.service.PermissionService;
import dev.hieunv.totp_bankos.service.TransferService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferServiceImpl implements TransferService {

    private static final String PERM_CREATE  = "TRANSFER:CREATE_REQUEST";
    private static final String PERM_APPROVE = "TRANSFER:APPROVE";

    private final TransferRequestRepository transferRepository;
    private final AccountWalletRepository   walletRepository;
    private final TransferRequestMapper     mapper;
    private final PermissionService         permissionService;

    // ── create ────────────────────────────────────────────────

    @Transactional
    @Override
    public TransferRequestResponse createRequest(Long walletId, Long makerId,
                                                 CreateTransferRequest request) {
        // wallet must exist and be active
        AccountWallet wallet = walletRepository.findById(walletId)
                .filter(AccountWallet::isActive)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        // basic balance pre-check — prevents obviously impossible requests
        if (request.getAmount().compareTo(wallet.getBalance()) > 0) {
            throw new BadRequestException("Insufficient wallet balance");
        }

        TransferRequest entity = mapper.toEntity(request);
        entity.setWalletId(walletId);
        entity.setCreatedBy(makerId);
        entity.setStatus(TransferRequest.Status.PENDING);
        entity.setCreatedAt(LocalDateTime.now());

        TransferRequest saved = transferRepository.save(entity);
        log.info("Transfer request {} created by user {} for wallet {}", saved.getId(), makerId, walletId);
        return mapper.toResponse(saved);
    }

    // ── read ──────────────────────────────────────────────────

    @Override
    public TransferRequestResponse getRequest(Long walletId, Long requestId) {
        TransferRequest entity = findAndVerifyWallet(requestId, walletId);
        return mapper.toResponse(entity);
    }

    @Override
    public PageResponse<TransferRequestResponse> listRequests(Long walletId, Long userId,
                                                              int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        boolean canApprove = permissionService.userHasPermission(userId, walletId, PERM_APPROVE);

        Page<TransferRequest> result;
        if (canApprove) {
            // checkers see only PENDING — that's their work queue
            result = transferRepository.findByWalletIdAndStatusOrderByCreatedAtDesc(
                    walletId, TransferRequest.Status.PENDING, pageable);
        } else {
            // makers see only their own requests
            result = transferRepository.findByWalletIdAndCreatedByOrderByCreatedAtDesc(
                    walletId, userId, pageable);
        }

        return toPageResponse(result);
    }

    // ── approve ───────────────────────────────────────────────

    @Override
    @Transactional
    public TransferRequestResponse approve(Long walletId, Long checkerId,
                                           Long requestId, ReviewRequest review) {
        TransferRequest entity = findPending(requestId, walletId);

        // Maker/checker rule: same user can self-approve ONLY if they hold both permissions.
        // @RequiresPermission("TRANSFER:APPROVE") already confirmed the caller has APPROVE.
        // We still check CREATE_REQUEST here so the rule is explicit at the service layer.
        boolean isSelfApproval = entity.getCreatedBy().equals(checkerId);
        if (isSelfApproval) {
            boolean makerToo = permissionService.userHasPermission(checkerId, walletId, PERM_CREATE);
            if (!makerToo) {
                throw new ForbiddenException("Checker cannot approve their own request");
            }
            log.info("Self-approval permitted for user {} (holds both Maker and Checker permissions)", checkerId);
        }

        // debit the wallet balance atomically in the same transaction
        AccountWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        if (entity.getAmount().compareTo(wallet.getBalance()) > 0) {
            throw new BadRequestException("Insufficient balance at time of approval");
        }

        wallet.setBalance(wallet.getBalance().subtract(entity.getAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        entity.setStatus(TransferRequest.Status.APPROVED);
        entity.setReviewedBy(checkerId);
        entity.setReviewedAt(LocalDateTime.now());

        TransferRequest saved = transferRepository.save(entity);
        log.info("Transfer {} approved by {} — wallet {} debited {}", requestId, checkerId, walletId, entity.getAmount());
        return mapper.toResponse(saved);
    }

    // ── reject ────────────────────────────────────────────────

    @Transactional
    @Override
    public TransferRequestResponse reject(Long walletId, Long checkerId,
                                          Long requestId, ReviewRequest review) {
        if (review == null || review.getNote() == null || review.getNote().isBlank()) {
            throw new BadRequestException("Rejection note is required");
        }

        TransferRequest entity = findPending(requestId, walletId);

        entity.setStatus(TransferRequest.Status.REJECTED);
        entity.setReviewedBy(checkerId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setRejectionNote(review.getNote());

        TransferRequest saved = transferRepository.save(entity);
        log.info("Transfer {} rejected by {}", requestId, checkerId);
        return mapper.toResponse(saved);
    }

    // ── private helpers ───────────────────────────────────────

    private TransferRequest findAndVerifyWallet(Long requestId, Long walletId) {
        TransferRequest entity = transferRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Transfer request not found"));
        if (!entity.getWalletId().equals(walletId)) {
            throw new ForbiddenException("Transfer request does not belong to this wallet");
        }
        return entity;
    }

    private TransferRequest findPending(Long requestId, Long walletId) {
        TransferRequest entity = findAndVerifyWallet(requestId, walletId);
        if (entity.getStatus() != TransferRequest.Status.PENDING) {
            throw new BadRequestException(
                    "Transfer request is already " + entity.getStatus().name().toLowerCase());
        }
        return entity;
    }

    private PageResponse<TransferRequestResponse> toPageResponse(Page<TransferRequest> page) {
        return PageResponse.<TransferRequestResponse>builder()
                .content(mapper.toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}