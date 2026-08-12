package dev.hieunv.riskassessment.dto.screening;

import dev.hieunv.riskassessment.dto.PageResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Màn hình "Chi tiết lịch sử phân loại rủi ro" — phần đầu trang + bảng lịch sử. */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerRiskHistoryResponse {

    private CustomerInfo customer;
    private PageResponse<RiskHistoryRow> history;

    /**
     * Bốn ô read-only ở đầu màn hình. Lấy từ bản chiếu của PCRT, đúng như yêu cầu — kèm hệ quả
     * là trường nào Core chưa cấp thì ở đây là NULL, xem {@code idNumber}.
     */
    @Getter
    @Setter
    @ToString
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomerInfo {
        private String cif;
        private String fullName;
        private String phone;
        private String idNumber;
    }
}
