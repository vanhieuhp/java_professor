package dev.hieunv.riskassessment.dto.screening;

import dev.hieunv.riskassessment.constant.RiskLevel;
import dev.hieunv.riskassessment.utils.DisplayTime;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Một dòng của bảng "Kết quả screening".
 *
 * <h2>Vì sao là constructor chứ không phải builder</h2>
 * Đối tượng này được dựng bằng biểu thức {@code SELECT new ...} trong JPQL, nên chữ ký
 * constructor chính là hợp đồng với câu truy vấn — đổi thứ tự tham số ở đây mà quên đổi câu
 * truy vấn thì Hibernate báo lỗi lúc khởi động, chứ không âm thầm đổi chỗ hai cột.
 */
@Getter
@ToString
public class ScreeningResultRow {

    private final String cif;

    /**
     * Nguyên văn từ bản chiếu. NULL khi CIF chưa từng được đồng bộ — trả NULL chứ không thay
     * bằng tên đã chuẩn hóa: "chưa có dữ liệu" và "tên khách hàng là NGUYEN VAN AN" là hai
     * chuyện khác nhau.
     */
    private final String customerName;

    private final String idNumber;

    private final RiskLevel riskLevel;
    private final Short riskScore;
    private final String reason;

    /** Mốc ghi kết quả vào CSDL PCRT. */
    private final Instant assessedAt;

    public ScreeningResultRow(String cif, String customerName, String idNumber,
                              RiskLevel riskLevel, Short riskScore, String reason,
                              Instant assessedAt) {
        this.cif = cif;
        this.customerName = customerName;
        this.idNumber = idNumber;
        this.riskLevel = riskLevel;
        this.riskScore = riskScore;
        this.reason = reason;
        this.assessedAt = assessedAt;
    }

    /** dd/MM/yyyy HH:mm:ss theo giờ Việt Nam — xem {@link DisplayTime}. */
    public String getAssessedAtText() {
        return DisplayTime.format(assessedAt);
    }
}
