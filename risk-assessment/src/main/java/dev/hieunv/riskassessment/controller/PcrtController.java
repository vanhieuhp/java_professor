package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.dto.WatchlistStatusResponse;
import dev.hieunv.riskassessment.matching.CompiledCategory;
import dev.hieunv.riskassessment.service.CustomerAmlService;
import dev.hieunv.riskassessment.service.CustomerEvaluationService;
import dev.hieunv.riskassessment.service.WatchlistServiceImpl;
import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pcrt")
@RequiredArgsConstructor
public class PcrtController {

    private final CustomerEvaluationService evaluationService;
    private final CustomerAmlService customerAmlService;
    private final WatchlistServiceImpl blackListServiceImpl;

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
     * tăng dần và dừng ở lần trùng đầu tiên (A.5-B3).
     */
    @PostMapping("/customers/evaluate-periodic")
    public ResponseEntity<EvaluateCustomerResponse> evaluatePeriodic(
            @Valid @RequestBody CustomerEvaluateRequest request) {
        return ResponseEntity.ok(evaluationService.evaluateAgainstWatchlists(request));
    }

    /**
     * Xem engine đang cầm dữ liệu gì trong RAM, theo đúng thứ tự nó sẽ duyệt.
     */
    @GetMapping("/watchlists")
    public ResponseEntity<WatchlistStatusResponse> watchlists() {
        WatchlistSnapshot snapshot = blackListServiceImpl.snapshot();

        List<CompiledCategory> all = new ArrayList<>();
        if (snapshot.getBlacklist() != null) {
            all.add(snapshot.getBlacklist());
        }
        all.addAll(snapshot.getCifEvaluateLists());

        List<WatchlistStatusResponse.CategoryStatus> categories = all.stream()
                .map(c -> WatchlistStatusResponse.CategoryStatus.builder()
                        .priority(c.getCategory().getPriority())
                        .subOrder(c.getCategory().getSubOrder())
                        .code(c.getCategory().getCode())
                        .name(c.getCategory().getName())
                        .matchType(c.getCategory().getMatchType())
                        .riskLevel(c.getCategory().getRiskLevel())
                        .riskScore(c.getCategory().getRiskScore())
                        .entryCount(c.getEntryCount())
                        .blacklist(c.getCategory().isBlacklist())
                        .build())
                .toList();

        return ResponseEntity.ok(WatchlistStatusResponse.builder()
                .loadedFrom(snapshot.getLoadedFrom())
                .totalEntries(categories.stream().mapToInt(WatchlistStatusResponse.CategoryStatus::getEntryCount).sum())
                .categories(categories)
                .build());
    }

    /**
     * Nạp lại cache ngay, không đợi chu kỳ kiểm tra nền.
     */
    @PostMapping("/watchlists/reload")
    public ResponseEntity<WatchlistStatusResponse> reload() {
        blackListServiceImpl.reload();
        return watchlists();
    }
}
