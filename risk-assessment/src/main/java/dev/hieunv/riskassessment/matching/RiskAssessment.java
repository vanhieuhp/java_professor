package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.constant.RiskLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.Set;

/**
 * Kết quả đánh giá rủi ro của một khách hàng.
 * <p>
 * Bốn trường đầu là đúng những gì spec A.4-B3 yêu cầu trả về Core ví:
 * <b>số CIF, mức rủi ro, điểm rủi ro, Lý do</b>. Phần còn lại là vết truy xuất — trùng
 * danh sách nào, bản ghi nào, qua những trường nào. Không có phần đó thì khi thanh tra hỏi
 * "tại sao khóa ví khách hàng này?", hệ thống không trả lời được.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RiskAssessment {

    private String cif;
    private RiskLevel riskLevel;
    private Short riskScore;
    private String reason;

    private String categoryCode;
    private String categoryName;
    private Short priority;
    private Long entryId;
    private Set<MatchField> matchedFields;

    /** True khi trùng DS đen — Core phải cập nhật điểm VÀ chạy quy trình khóa CIF. */
    private boolean lockCifRequired;
}
