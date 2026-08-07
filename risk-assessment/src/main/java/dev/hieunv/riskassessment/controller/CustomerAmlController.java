package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistStatusResponse;
import dev.hieunv.riskassessment.service.CustomerAmlService;
import dev.hieunv.riskassessment.service.WatchlistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/pcrt")
@RequiredArgsConstructor
public class CustomerAmlController {

    private final CustomerAmlService customerAmlService;
    private final WatchlistService watchlistService;

    /**
     * TH2 — BE ví CN gọi sau khi KH eKYC hoặc cập nhật thông tin thành công (A.4-B1).
     * Chỉ so khớp DS đen. Trùng → điểm 7, mức cao, Core phải khóa CIF.
     */
    @PostMapping("/customers/evaluate")
    public ResponseEntity<EvaluateCustomerResponse> evaluate(@Valid @RequestBody CustomerEvaluateRequest request) {
        return ResponseEntity.ok(customerAmlService.evaluateAgainstBlacklist(request));
    }

    /**
     * TH3 áp cho một khách hàng — so khớp toàn bộ tiêu chí trừ DS đen, duyệt theo mức ưu tiên
     * tăng dần và dừng ở lần trùng đầu tiên.
     */
    @PostMapping("/customers/evaluate-periodic")
    public ResponseEntity<EvaluateCustomerResponse> evaluatePeriodic(@Valid @RequestBody CustomerEvaluateRequest request) {
        return ResponseEntity.ok(customerAmlService.evaluateAgainstWatchlists(request));
    }

    @GetMapping("/watchlists")
    public ResponseEntity<WatchlistStatusResponse> watchlists() {
        WatchlistStatusResponse response = watchlistService.getWatchlistStatus();
        return ResponseEntity.ok(response);
    }

    /**
     * Nạp lại cache ngay, không đợi chu kỳ kiểm tra nền.
     */
    @PostMapping("/watchlists/reload")
    public ResponseEntity<WatchlistStatusResponse> reload() {
        watchlistService.reload();
        return watchlists();
    }
}
