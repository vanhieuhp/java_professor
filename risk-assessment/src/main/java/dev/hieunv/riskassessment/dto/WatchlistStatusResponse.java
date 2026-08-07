package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.constant.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

/** Trạng thái cache danh sách đang nằm trong RAM — để nhìn thấy engine đang cầm dữ liệu gì. */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistStatusResponse {

    private Instant loadedFrom;
    private int totalEntries;
    private List<CategoryStatus> categories;

    @Getter
    @Setter
    @ToString
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStatus {
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
}
