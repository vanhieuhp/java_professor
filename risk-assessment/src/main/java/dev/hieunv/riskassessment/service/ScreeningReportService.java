package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.PageResponse;
import dev.hieunv.riskassessment.dto.screening.CustomerRiskHistoryResponse;
import dev.hieunv.riskassessment.dto.screening.ScreeningFilterOptions;
import dev.hieunv.riskassessment.dto.screening.ScreeningResultRow;
import dev.hieunv.riskassessment.dto.screening.ScreeningSearchRequest;

import java.util.Optional;

/** Hai màn hình chỉ-đọc của web admin: danh sách kết quả screening và lịch sử của một khách hàng. */
public interface ScreeningReportService {

    /** Nội dung ba dropdown. Gọi một lần lúc mở màn hình. */
    ScreeningFilterOptions filterOptions();

    PageResponse<ScreeningResultRow> search(ScreeningSearchRequest request);

    /** {@code Optional.empty()} = PCRT chưa từng biết CIF này, web trả 404. */
    Optional<CustomerRiskHistoryResponse> history(String cif, int page, int size);
}
