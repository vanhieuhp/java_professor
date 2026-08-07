package dev.hieunv.riskassessment.dto.watchlist;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.constant.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCategoryDto {
    private Short priority;
    private Short subOrder;
    private String code;
    private String name;
    private MatchType matchType;
    private RiskLevel riskLevel;
    private Short riskScore;
    private int entryCount;
    private boolean blacklist;
}
