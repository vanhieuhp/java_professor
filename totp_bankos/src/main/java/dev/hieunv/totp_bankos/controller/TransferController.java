package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.CreateTransferRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.dto.response.TransferRequestResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    @GetMapping
    @RequiresPermission("TRANSFER:LIST")
    public ResponseEntity<ApiResponse<PageResponse<TransferRequestResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok(
                transferService.listRequests(walletId, userId, page, size)));
    }

    @GetMapping("/{requestId}")
    @RequiresPermission("TRANSFER:READ_DETAIL")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> getDetail(
            @PathVariable Long requestId) {

        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok(
                transferService.getRequest(walletId, requestId)));
    }

    @PostMapping
    @RequiresPermission("TRANSFER:CREATE_REQUEST")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> createRequest(
            @Valid @RequestBody CreateTransferRequest request) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Transfer request created",
                        transferService.createRequest(walletId, userId, request)));
    }

    @PostMapping("/{requestId}/approve")
    @RequiresPermission("TRANSFER:APPROVE")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReviewRequest review) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok("Transfer approved",
                transferService.approve(walletId, userId, requestId, review)));
    }

    @PostMapping("/{requestId}/reject")
    @RequiresPermission("TRANSFER:APPROVE")
    public ResponseEntity<ApiResponse<TransferRequestResponse>> reject(
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewRequest review) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok("Transfer rejected",
                transferService.reject(walletId, userId, requestId, review)));
    }

    @GetMapping("/export")
    @RequiresPermission("TRANSFER:EXPORT_EXCEL")
    public ResponseEntity<ApiResponse<Object>> export() {
        return ResponseEntity.ok(ApiResponse.ok("Export triggered", null));
    }
}