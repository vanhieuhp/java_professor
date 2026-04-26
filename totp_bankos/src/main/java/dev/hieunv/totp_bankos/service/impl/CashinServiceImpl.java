package dev.hieunv.totp_bankos.service.impl;

import dev.hieunv.totp_bankos.domain.AccountWallet;
import dev.hieunv.totp_bankos.domain.CashinRequest;
import dev.hieunv.totp_bankos.dto.request.CreateCashinRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.CashinRequestResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.exception.BadRequestException;
import dev.hieunv.totp_bankos.exception.ForbiddenException;
import dev.hieunv.totp_bankos.exception.NotFoundException;
import dev.hieunv.totp_bankos.mapper.CashinRequestMapper;
import dev.hieunv.totp_bankos.repository.AccountWalletRepository;
import dev.hieunv.totp_bankos.repository.CashinRequestRepository;
import dev.hieunv.totp_bankos.service.CashinService;
import dev.hieunv.totp_bankos.service.PermissionService;
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
public class CashinServiceImpl implements CashinService {

    private static final String PERM_CREATE  = "CASHIN:CREATE_REQUEST";
    private static final String PERM_APPROVE = "CASHIN:APPROVE";

    private final CashinRequestRepository cashinRepository;
    private final AccountWalletRepository walletRepository;
    private final CashinRequestMapper     mapper;
    private final PermissionService       permissionService;

    @Override
    @Transactional
    public CashinRequestResponse createRequest(Long walletId, Long makerId,
                                               CreateCashinRequest request) {
        if (!walletRepository.existsById(walletId)) {
            throw new NotFoundException("Wallet not found");
        }

        CashinRequest entity = mapper.toEntity(request);
        entity.setWalletId(walletId);
        entity.setCreatedBy(makerId);
        entity.setStatus(CashinRequest.Status.PENDING);
        entity.setCreatedAt(LocalDateTime.now());

        CashinRequest saved = cashinRepository.save(entity);
        log.info("Cash-in request {} created by user {} for wallet {}", saved.getId(), makerId, walletId);
        return mapper.toResponse(saved);
    }

    @Override
    public CashinRequestResponse getRequest(Long walletId, Long requestId) {
        return mapper.toResponse(findAndVerifyWallet(requestId, walletId));
    }

    @Override
    public PageResponse<CashinRequestResponse> listRequests(Long walletId, Long userId,
                                                            int page, int size) {
        PageRequest pageable = PageRequest.of(page, size);
        boolean canApprove = permissionService.userHasPermission(userId, walletId, PERM_APPROVE);

        Page<CashinRequest> result;
        if (canApprove) {
            result = cashinRepository.findByWalletIdAndStatusOrderByCreatedAtDesc(
                    walletId, CashinRequest.Status.PENDING, pageable);
        } else {
            result = cashinRepository.findByWalletIdAndCreatedByOrderByCreatedAtDesc(
                    walletId, userId, pageable);
        }

        return toPageResponse(result);
    }

    @Override
    @Transactional
    public CashinRequestResponse approve(Long walletId, Long checkerId,
                                         Long requestId, ReviewRequest review) {
        CashinRequest entity = findPending(requestId, walletId);

        // same self-approval rule as transfers
        boolean isSelfApproval = entity.getCreatedBy().equals(checkerId);
        if (isSelfApproval) {
            boolean makerToo = permissionService.userHasPermission(checkerId, walletId, PERM_CREATE);
            if (!makerToo) {
                throw new ForbiddenException("Checker cannot approve their own request");
            }
        }

        // credit wallet balance
        AccountWallet wallet = walletRepository.findById(walletId)
                .orElseThrow(() -> new NotFoundException("Wallet not found"));

        wallet.setBalance(wallet.getBalance().add(entity.getAmount()));
        wallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(wallet);

        entity.setStatus(CashinRequest.Status.APPROVED);
        entity.setReviewedBy(checkerId);
        entity.setReviewedAt(LocalDateTime.now());

        CashinRequest saved = cashinRepository.save(entity);
        log.info("Cash-in {} approved by {} — wallet {} credited {}", requestId, checkerId, walletId, entity.getAmount());
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CashinRequestResponse reject(Long walletId, Long checkerId,
                                        Long requestId, ReviewRequest review) {
        if (review == null || review.getNote() == null || review.getNote().isBlank()) {
            throw new BadRequestException("Rejection note is required");
        }

        CashinRequest entity = findPending(requestId, walletId);

        entity.setStatus(CashinRequest.Status.REJECTED);
        entity.setReviewedBy(checkerId);
        entity.setReviewedAt(LocalDateTime.now());
        entity.setRejectionNote(review.getNote());

        CashinRequest saved = cashinRepository.save(entity);
        log.info("Cash-in {} rejected by {}", requestId, checkerId);
        return mapper.toResponse(saved);
    }

    // ── private helpers ───────────────────────────────────────

    private CashinRequest findAndVerifyWallet(Long requestId, Long walletId) {
        CashinRequest entity = cashinRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Cash-in request not found"));
        if (!entity.getWalletId().equals(walletId)) {
            throw new ForbiddenException("Cash-in request does not belong to this wallet");
        }
        return entity;
    }

    private CashinRequest findPending(Long requestId, Long walletId) {
        CashinRequest entity = findAndVerifyWallet(requestId, walletId);
        if (entity.getStatus() != CashinRequest.Status.PENDING) {
            throw new BadRequestException(
                    "Cash-in request is already " + entity.getStatus().name().toLowerCase());
        }
        return entity;
    }

    private PageResponse<CashinRequestResponse> toPageResponse(Page<CashinRequest> page) {
        return PageResponse.<CashinRequestResponse>builder()
                .content(mapper.toResponseList(page.getContent()))
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .last(page.isLast())
                .build();
    }
}