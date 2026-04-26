package dev.hieunv.totp_bankos.service;

import dev.hieunv.totp_bankos.dto.request.CreateCashinRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.CashinRequestResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;

public interface CashinService {

    CashinRequestResponse createRequest(Long walletId, Long makerId,
                                        CreateCashinRequest request);

    CashinRequestResponse getRequest(Long walletId, Long requestId);

    PageResponse<CashinRequestResponse> listRequests(Long walletId, Long userId,
                                                     int page, int size);

    // balance is credited to wallet on approval
    CashinRequestResponse approve(Long walletId, Long checkerId,
                                  Long requestId, ReviewRequest review);

    CashinRequestResponse reject(Long walletId, Long checkerId,
                                 Long requestId, ReviewRequest review);
}