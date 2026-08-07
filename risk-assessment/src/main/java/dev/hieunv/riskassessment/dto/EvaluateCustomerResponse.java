package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.matching.MatchField;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * Kết quả trả về sau khi đánh giá.
 * <p>
 * Bốn trường {@code cif / riskLevel / riskScore / reason} là đúng contract spec A.4-B3
 * yêu cầu gửi Core ví. Các trường còn lại là vết truy xuất và thông tin vận hành — trong
 * hệ thống thật chúng nằm lại ở PCRT để giải trình, không nhất thiết gửi sang Core.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EvaluateCustomerResponse {

    private String cif;

    /** false = không trùng danh sách nào. Theo spec A.4-B2 nhánh này KHÔNG gọi Core. */
    private boolean matched;

    // ----- Contract gửi Core (A.4-B3) -----
    private RiskLevel riskLevel;
    private Short riskScore;
    private String reason;

    // ----- Vết truy xuất -----
    private String categoryCode;
    private String categoryName;
    private Short priority;
    private Long entryId;
    private Set<MatchField> matchedFields;

    /** true → Core phải cập nhật điểm VÀ chạy quy trình khóa CIF. Chỉ xảy ra khi trùng DS đen. */
    private boolean lockCifRequired;

    // ----- Vận hành -----
    private TriggerType triggerType;
    private Long scanQueueId;
    private String scanBatchId;
    private long elapsedMillis;
}
