package dev.hieunv.totp_bankos.controller;

import dev.hieunv.totp_bankos.dto.request.CreateCifRequest;
import dev.hieunv.totp_bankos.dto.response.ApiResponse;
import dev.hieunv.totp_bankos.dto.response.CifResponse;
import dev.hieunv.totp_bankos.security.RequiresPermission;
import dev.hieunv.totp_bankos.service.CifService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/cif")
@RequiredArgsConstructor
public class CifController {

    private final CifService cifService;

    @PostMapping
    @RequiresPermission("ADMIN:CREATE_CIF")
    public ResponseEntity<ApiResponse<CifResponse>> create(@Valid @RequestBody CreateCifRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("CIF created", cifService.create(request)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CifResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(cifService.getById(id)));
    }

    @GetMapping("/code/{code}")
    public ResponseEntity<ApiResponse<CifResponse>> getByCode(@PathVariable String code) {
        return ResponseEntity.ok(ApiResponse.ok(cifService.getByCode(code)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CifResponse>>> listAll() {
        return ResponseEntity.ok(ApiResponse.ok(cifService.listAll()));
    }

    @PatchMapping("/{id}/deactivate")
    public ResponseEntity<ApiResponse<CifResponse>> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("CIF deactivated", cifService.deactivate(id)));
    }
}