package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.dto.PageResponse;
import dev.hieunv.riskassessment.dto.screening.CustomerRiskHistoryResponse;
import dev.hieunv.riskassessment.dto.screening.RiskHistoryRow;
import dev.hieunv.riskassessment.dto.screening.ScreeningFilterOptions;
import dev.hieunv.riskassessment.dto.screening.ScreeningResultRow;
import dev.hieunv.riskassessment.dto.screening.ScreeningSearchRequest;
import dev.hieunv.riskassessment.entity.CustomerIdentity;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.repository.CustomerIdentityRepository;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.service.ScreeningReportService;
import dev.hieunv.riskassessment.utils.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScreeningReportServiceImpl implements ScreeningReportService {

    /** Điểm rủi ro theo bảng A.6 — số tự nhiên 1..7. */
    private static final List<Integer> RISK_SCORES = List.of(1, 2, 3, 4, 5, 6, 7);

    /**
     * Danh sách giữ chỗ cho tham số {@code IN} khi bộ lọc điểm đang tắt. Không bao giờ khớp
     * dòng nào, và tồn tại chỉ vì {@code IN ()} không phải SQL hợp lệ.
     */
    private static final Collection<Short> UNUSED_SCORE_LIST = List.of((short) -1);

    private final CustomerRiskResultRepository riskResultRepository;
    private final CustomerIdentityRepository identityRepository;
    private final WatchlistCategoryRepository categoryRepository;

    @Override
    @Transactional(readOnly = true)
    public ScreeningFilterOptions filterOptions() {
        return ScreeningFilterOptions.builder()
                .riskLevels(Arrays.stream(RiskLevel.values())
                        .map(level -> ScreeningFilterOptions.Option.builder()
                                .value(level.name())
                                .label(level.getLabel())
                                .build())
                        .toList())
                .riskScores(RISK_SCORES)
                .reasons(reasonOptions())
                .build();
    }

    /**
     * Lý do lấy từ danh mục đang bật.
     *
     * <h2>Vì sao gộp theo mã chứ không trả thẳng danh sách danh mục</h2>
     * Một mã danh mục có thể xuất hiện nhiều lần (khác {@code match_type}), và một dropdown có
     * hai dòng chữ giống hệt nhau là lỗi nhìn thấy được. Gộp bằng {@link LinkedHashMap} để
     * giữ nguyên thứ tự ưu tiên mà câu truy vấn đã sắp.
     */
    private List<ScreeningFilterOptions.Option> reasonOptions() {
        Map<String, String> byCode = new LinkedHashMap<>();
        categoryRepository.findBlacklistCategory()
                .ifPresent(c -> byCode.put(c.getCode(), c.getName()));
        for (WatchlistCategory c : categoryRepository.findCifWatchlist()) {
            byCode.putIfAbsent(c.getCode(), c.getName());
        }
        return byCode.entrySet().stream()
                .map(e -> ScreeningFilterOptions.Option.builder()
                        .value(e.getKey())
                        .label(e.getValue())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ScreeningResultRow> search(ScreeningSearchRequest request) {
        boolean anyScore = request.getRiskScores() == null || request.getRiskScores().isEmpty();

        Page<ScreeningResultRow> page = riskResultRepository.searchLatestResults(
                request.getRiskLevel() == null,
                request.getRiskLevel(),
                anyScore,
                anyScore ? UNUSED_SCORE_LIST : request.getRiskScores(),
                isBlank(request.getReason()),
                request.getReason(),
                // SĐT đã được validator chặn ký tự lạ nên không chuẩn hóa lại ở đây: chạy
                // Normalizer.phone trên một MẢNH số sẽ thêm số 0 vào đầu ("912" -> "0912") và
                // biến tìm tương đối thành tìm sai.
                likePattern(request.getPhone()),
                likePattern(Normalizer.idNumber(request.getIdNumber())),
                PageRequest.of(request.getPage(), request.getSize()));

        return PageResponse.of(page, row -> row);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerRiskHistoryResponse> history(String cif, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<RiskHistoryRow> history = riskResultRepository.findHistoryByCif(cif, pageable);
        Optional<CustomerIdentity> identity = identityRepository.findById(cif);

        // Không có cả bản chiếu lẫn lịch sử = PCRT chưa từng nghe tới CIF này. Trả 404 thay vì
        // một màn hình trống bốn ô rỗng, vì hai tình huống đó cần hai phản ứng khác nhau: gõ
        // sai số CIF, hay khách hàng có thật nhưng chưa lần nào bị đánh giá.
        if (identity.isEmpty() && history.isEmpty()) {
            return Optional.empty();
        }

        int offset = page * size;
        List<RiskHistoryRow> rows = history.getContent();
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setSequence(offset + i + 1);
        }

        return Optional.of(CustomerRiskHistoryResponse.builder()
                .customer(customerInfo(cif, identity.orElse(null)))
                .history(PageResponse.of(history, row -> row))
                .build());
    }

    private static CustomerRiskHistoryResponse.CustomerInfo customerInfo(String cif, CustomerIdentity i) {
        return CustomerRiskHistoryResponse.CustomerInfo.builder()
                .cif(cif)
                .fullName(i == null ? null : i.getFullName())
                .phone(i == null ? null : i.getPhone())
                .idNumber(i == null ? null : i.getIdNumber())
                .build();
    }

    /** {@code null} = không lọc. Bọc {@code %} ở hai đầu vì cả hai ô đều tìm tương đối. */
    private static String likePattern(String raw) {
        if (isBlank(raw)) {
            return null;
        }
        return "%" + raw.trim() + "%";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
