package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.constant.RiskLevel;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Contract PCRT → Core ví.
 * <p>
 * Bốn trường nghiệp vụ là đúng những gì spec A.4-B3 liệt kê: <b>số CIF, mức rủi ro,
 * điểm rủi ro, Lý do</b>. Hai trường còn lại là hạ tầng:
 * <ul>
 *   <li>{@code idempotencyKey} — để Core nhận diện lệnh lặp;</li>
 *   <li>{@code lockCif} — chốt <b>Q1</b>: TH1/TH2 gửi {@code true} (Core cập nhật điểm VÀ
 *       khóa CIF), TH3 gửi {@code false} (chỉ cập nhật điểm). Quyết định nằm ở PCRT vì chỉ
 *       PCRT biết kết quả đến từ DS đen hay DS mẫu — Core không nên phải suy ra điều đó từ
 *       con số điểm.</li>
 * </ul>
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreRiskUpdateRequest {

    /**
     * Khóa chống trùng, sinh từ {@code customer_risk_result.id}.
     * <p>
     * Phải ổn định qua các lần gửi lại — nếu mỗi lần thử sinh một khóa mới thì nó vô dụng,
     * Core sẽ coi mỗi lần là một lệnh khác nhau và khóa CIF nhiều lần.
     */
    @NotBlank
    private String idempotencyKey;

    @NotBlank
    private String cif;

    @NotNull
    private RiskLevel riskLevel;

    @NotNull
    private Short riskScore;

    @NotBlank
    private String reason;

    /** true → Core cập nhật điểm VÀ chạy quy trình khóa CIF. Chỉ đúng với TH1/TH2. */
    private boolean lockCif;
}
