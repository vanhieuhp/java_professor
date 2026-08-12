package dev.hieunv.riskassessment.dto.screening;

import dev.hieunv.riskassessment.constant.RiskLevel;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Điều kiện tìm kiếm của màn hình "Kết quả screening".
 *
 * <h2>Quy ước rỗng = tất cả</h2>
 * Mọi trường đều không bắt buộc. Bỏ trống một trường nghĩa là trường đó không ràng buộc gì —
 * đúng với mục "Tất cả" trên web. Trong một trường nhiều giá trị thì các giá trị là OR
 * ({@code riskScores}); giữa các trường là AND.
 */
@Getter
@Setter
@ToString
public class ScreeningSearchRequest {

    /**
     * Tìm tương đối. Chặn ký tự không phải số ngay ở cổng thay vì lặng lẽ bỏ đi: gõ nhầm
     * "091-234" mà server tự cắt thành "091234" thì người dùng thấy một kết quả không khớp
     * với cái mình vừa gõ và không hiểu vì sao.
     */
    @Pattern(regexp = "\\d*", message = "Số điện thoại chỉ được chứa ký tự số")
    @Size(max = 20, message = "Số điện thoại quá dài")
    private String phone;

    /** Free-text, tìm tương đối. */
    @Size(max = 50, message = "Số GTTT quá dài")
    private String idNumber;

    /** Bỏ trống = tất cả các mức. */
    private RiskLevel riskLevel;

    /** Bỏ trống = tất cả các điểm. Nhiều giá trị = OR. */
    private List<@Min(1) @Max(7) Short> riskScores;

    /** Mã danh mục lý do ({@code watchlist_category.code}). Bỏ trống = tất cả. */
    private String reason;

    @Min(value = 0, message = "Số trang bắt đầu từ 0")
    private int page = 0;

    @Min(value = 1, message = "Cỡ trang tối thiểu là 1")
    @Max(value = 200, message = "Cỡ trang tối đa là 200")
    private int size = 20;
}
