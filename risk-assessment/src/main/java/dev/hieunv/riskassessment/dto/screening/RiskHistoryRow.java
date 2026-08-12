package dev.hieunv.riskassessment.dto.screening;

import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.utils.DisplayTime;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/** Một lượt đánh giá trong lịch sử của một khách hàng. */
@Getter
@ToString
public class RiskHistoryRow {

    /**
     * STT chạy theo TOÀN BỘ kết quả, không reset ở mỗi trang: trang 2 với cỡ trang 20 bắt đầu
     * từ 21. Đánh số lại từ 1 ở mỗi trang thì hai dòng khác nhau cùng mang số 1.
     */
    @Setter
    private int sequence;

    private final Instant assessedAt;
    private final RiskLevel riskLevel;
    private final Short riskScore;
    private final String reason;

    public RiskHistoryRow(Instant assessedAt, RiskLevel riskLevel, Short riskScore, String reason) {
        this.assessedAt = assessedAt;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.reason = reason;
    }

    public String getAssessedAtText() {
        return DisplayTime.format(assessedAt);
    }
}
