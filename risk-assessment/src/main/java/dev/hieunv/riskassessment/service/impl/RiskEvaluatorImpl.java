package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryIndex;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.IdentityMatcher;
import dev.hieunv.riskassessment.matching.MatchDetail;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import dev.hieunv.riskassessment.service.RiskEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Engine chấm điểm rủi ro. Không đụng DB, không biết gì về hàng đợi hay Core ví —
 * chỉ nhận dữ liệu khách hàng cộng một ảnh chụp danh sách, trả về kết quả.
 * Cả 3 flow (TH1, TH2, TH3) dùng chung class này.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEvaluatorImpl implements RiskEvaluator {

    private final IdentityMatcher matcher;

    /**
     * TH3 — duyệt 15 DS mẫu theo mức ưu tiên TĂNG DẦN, dừng ngay ở lần trùng ĐẦU TIÊN
     */
    @Override
    public Optional<RiskAssessment> evaluateAgainstWatchlists(CustomerSnapshot customer,
                                                              WatchlistSnapshot lists) {
        for (WatchlistCategoryIndex category : lists.getCifEvaluateLists()) {
            Optional<MatchDetail> detail = evaluateMatch(customer, category);
            if (detail.isPresent()) {
                return detail.map(d -> toAssessment(customer, category.getCategory(), d, false));
            }
        }
        return Optional.empty();
    }

    /**
     * TH1 và TH2 — chỉ so khớp DS đen.
     * Trùng thì điểm 7, mức cao, và Core phải chạy quy trình khóa CIF.
     */
    @Override
    public Optional<RiskAssessment> evaluateAgainstBlacklist(CustomerSnapshot customer,
                                                             WatchlistSnapshot lists) {
        WatchlistCategoryIndex blacklist = lists.getBlacklist();
        if (blacklist == null) {
            log.warn("No blacklist configured — skipping evaluation for CIF {}", customer.getCif());
            return Optional.empty();
        }
        return matcher.matchCifIdentity(customer, blacklist.getCifIdentityIndex())
                .map(detail -> toAssessment(customer, blacklist.getCategory(), detail, true));
    }

    public Optional<MatchDetail> evaluateMatch(CustomerSnapshot customer, WatchlistCategoryIndex index) {
        if (index.getCategory().getMatchType() == MatchType.K1) {
            return matcher.matchCifIdentity(customer, index.getCifIdentityIndex());
        } else {
            return matcher.matchCifAttribute(customer, index.getCifAttributeIndex());
        }
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
