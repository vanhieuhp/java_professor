package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.matching.CompiledCategory;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.MatchDetail;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Engine chấm điểm rủi ro. Không đụng DB, không biết gì về hàng đợi hay Core ví —
 * chỉ nhận dữ liệu khách hàng cộng một ảnh chụp danh sách, trả về kết quả.
 * <p>
 * Cả 3 flow (TH1, TH2, TH3) dùng chung class này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluator {

    /**
     * TH1 và TH2 — chỉ so khớp DS đen.
     * <p>
     * Trùng thì điểm 7, mức cao, và Core phải chạy quy trình khóa CIF (A.3-B4, A.4-B4).
     */
    public Optional<RiskAssessment> evaluateAgainstBlacklist(CustomerSnapshot customer,
                                                             WatchlistSnapshot lists) {
        CompiledCategory blacklist = lists.getBlacklist();
        if (blacklist == null) {
            log.warn("Chưa cấu hình DS đen — bỏ qua đánh giá cho CIF {}", customer.getCif());
            return Optional.empty();
        }
        return blacklist.match(customer)
                .map(detail -> toAssessment(customer, blacklist.getCategory(), detail, true));
    }

    /**
     * TH3 — duyệt 15 DS mẫu theo mức ưu tiên TĂNG DẦN, dừng ngay ở lần trùng ĐẦU TIÊN
     * (A.5-B3).
     * <p>
     * Không cộng dồn điểm nhiều danh sách. Thang 7/5/4/3/2 là nhãn phân loại ordinal, không
     * phải đại lượng đo được — cộng ra 10 thì không có mức rủi ro nào ứng với nó và không có
     * lý do nào giải trình được. Mỗi kết quả phải quy về đúng MỘT bản ghi trong MỘT danh sách.
     * <p>
     * Thứ tự ưu tiên được thiết kế sao cho danh sách trùng đầu tiên cũng là danh sách nghiêm
     * trọng nhất, nên dừng sớm không làm mất kết quả nặng hơn — nó chỉ tiết kiệm công quét.
     */
    public Optional<RiskAssessment> evaluateAgainstWatchlists(CustomerSnapshot customer,
                                                              WatchlistSnapshot lists) {
        for (CompiledCategory category : lists.getCifEvaluateLists()) {
            Optional<MatchDetail> detail = category.match(customer);
            if (detail.isPresent()) {
                return detail.map(d -> toAssessment(customer, category.getCategory(), d, false));
            }
        }
        return Optional.empty();
    }

    private RiskAssessment toAssessment(CustomerSnapshot customer,
                                        WatchlistCategory category,
                                        MatchDetail detail,
                                        boolean lockCifRequired) {
        return RiskAssessment.builder()
                .cif(customer.getCif())
                .riskLevel(category.getRiskLevel())
                .riskScore(category.getRiskScore())
                .reason(category.getReason())
                .categoryCode(category.getCode())
                .categoryName(category.getName())
                .priority(category.getPriority())
                .entryId(detail.getEntryId())
                .matchedFields(detail.getMatchedFields())
                .lockCifRequired(lockCifRequired)
                .build();
    }
}
