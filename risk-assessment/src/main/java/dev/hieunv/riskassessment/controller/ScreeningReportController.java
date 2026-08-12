package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.dto.PageResponse;
import dev.hieunv.riskassessment.dto.screening.CustomerRiskHistoryResponse;
import dev.hieunv.riskassessment.dto.screening.ScreeningFilterOptions;
import dev.hieunv.riskassessment.dto.screening.ScreeningResultRow;
import dev.hieunv.riskassessment.dto.screening.ScreeningSearchRequest;
import dev.hieunv.riskassessment.service.ScreeningReportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Báo cáo screening cho web admin — CHỈ ĐỌC.
 *
 * <h2>Vì sao chỉ có GET</h2>
 * Cả hai màn hình đều là dữ liệu chỉ xem. Không mở đường ghi ở đây, kể cả để "sửa nhanh" một
 * kết quả sai: kết quả đánh giá là vết kiểm toán, sửa được nghĩa là không còn là vết.
 */
@RestController
@RequestMapping("/api/v1/pcrt/screening")
@RequiredArgsConstructor
@Validated
public class ScreeningReportController {

    private final ScreeningReportService screeningReportService;

    /**
     * Nội dung ba dropdown Mức rủi ro / Điểm rủi ro / Lý do.
     * <p>
     * Không kèm mục "Tất cả" — xem {@link ScreeningFilterOptions}.
     */
    @GetMapping("/filter-options")
    public ResponseEntity<ScreeningFilterOptions> filterOptions() {
        return ResponseEntity.ok(screeningReportService.filterOptions());
    }

    /**
     * Nút "Tìm kiếm". Bỏ trống tham số nào thì tham số đó không ràng buộc gì.
     * <p>
     * Trong một trường: OR ({@code riskScores=3&riskScores=4}). Giữa các trường: AND.
     * Không có dòng nào thỏa mãn thì {@code content} rỗng và {@code empty = true} — web hiển
     * thị bảng trống kèm dòng chữ "Không có dữ liệu"; đó không phải lỗi nên không trả 404.
     */
    @GetMapping("/results")
    public ResponseEntity<PageResponse<ScreeningResultRow>> search(
            @Valid @ModelAttribute ScreeningSearchRequest request) {
        return ResponseEntity.ok(screeningReportService.search(request));
    }

    /** Chi tiết lịch sử phân loại rủi ro của một khách hàng. */
    @GetMapping("/customers/{cif}/history")
    public ResponseEntity<CustomerRiskHistoryResponse> history(
            @PathVariable String cif,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(200) int size) {
        return screeningReportService.history(cif, page, size)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
