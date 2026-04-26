package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.CreateTransferRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.dto.response.TransferRequestResponse;
import org.springframework.transaction.annotation.Transactional;

public interface TransferService {
    TransferRequestResponse createRequest(Long walletId, Long makerId,
                                          CreateTransferRequest request);

    TransferRequestResponse getRequest(Long walletId, Long requestId);

    // Viewer / Maker / Checker all call list — service filters by their role
    PageResponse<TransferRequestResponse> listRequests(Long walletId, Long userId,
                                                       int page, int size);

    // Checker approves — balance is debited from wallet on approval
    TransferRequestResponse approve(Long walletId, Long checkerId,
                                    Long requestId, ReviewRequest review);

    // Checker rejects — note is required
    TransferRequestResponse reject(Long walletId, Long checkerId,
                                   Long requestId, ReviewRequest review);
}
