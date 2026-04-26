package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.CreateCashinRequest;
import dev.hieunv.totp_bankos.dto.request.ReviewRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.CashinRequestResponse;
import dev.hieunv.totp_bankos.dto.response.PageResponse;
import dev.hieunv.totp_bankos.security.AppSecurityContext;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.CashinService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cashin")
@RequiredArgsConstructor
public class CashinController {

    private final CashinService cashinService;

    @GetMapping
    @RequiresPermission("CASHIN:LIST")
    public ResponseEntity<ApiResponse<PageResponse<CashinRequestResponse>>> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok(
                cashinService.listRequests(walletId, userId, page, size)));
    }

    @GetMapping("/{requestId}")
    @RequiresPermission("CASHIN:READ_DETAIL")
    public ResponseEntity<ApiResponse<CashinRequestResponse>> getDetail(
            @PathVariable Long requestId) {

        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok(
                cashinService.getRequest(walletId, requestId)));
    }

    @PostMapping
    @RequiresPermission("CASHIN:CREATE_REQUEST")
    public ResponseEntity<ApiResponse<CashinRequestResponse>> createRequest(
            @Valid @RequestBody CreateCashinRequest request) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Cash-in request created",
                        cashinService.createRequest(walletId, userId, request)));
    }

    @PostMapping("/{requestId}/approve")
    @RequiresPermission("CASHIN:APPROVE")
    public ResponseEntity<ApiResponse<CashinRequestResponse>> approve(
            @PathVariable Long requestId,
            @RequestBody(required = false) ReviewRequest review) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok("Cash-in approved",
                cashinService.approve(walletId, userId, requestId, review)));
    }

    @PostMapping("/{requestId}/reject")
    @RequiresPermission("CASHIN:APPROVE")
    public ResponseEntity<ApiResponse<CashinRequestResponse>> reject(
            @PathVariable Long requestId,
            @Valid @RequestBody ReviewRequest review) {

        Long userId   = AppSecurityContext.getUserId();
        Long walletId = AppSecurityContext.getWalletId();
        return ResponseEntity.ok(ApiResponse.ok("Cash-in rejected",
                cashinService.reject(walletId, userId, requestId, review)));
    }

    @GetMapping("/export")
    @RequiresPermission("CASHIN:EXPORT_EXCEL")
    public ResponseEntity<ApiResponse<Object>> export() {
        return ResponseEntity.ok(ApiResponse.ok("Export triggered", null));
    }
}