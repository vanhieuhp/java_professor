package dev.hieunv.riskassessment.dto.screening;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

/**
 * Nội dung ba dropdown của màn hình tìm kiếm.
 *
 * <h2>Vì sao KHÔNG có mục "Tất cả" trong danh sách trả về</h2>
 * "Tất cả" không phải một giá trị lọc, nó là <b>trạng thái không lọc</b>. Nếu BE trả nó xuống
 * như một lựa chọn thì web sẽ gửi ngược lên một giá trị nào đó — {@code "ALL"}, chuỗi rỗng,
 * hay cả danh sách 7 điểm — và mỗi client sẽ chọn một kiểu. Hợp đồng ở đây: <b>bỏ trống tham
 * số</b> nghĩa là tất cả. Web tự thêm dòng "Tất cả" lên đầu và ánh xạ nó thành "không gửi
 * tham số".
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScreeningFilterOptions {

    /** Mức rủi ro đang dùng — hiện là Cao và Trung bình, KHÔNG có Thấp. */
    private List<Option> riskLevels;

    /** Điểm rủi ro: số tự nhiên 1..7. */
    private List<Integer> riskScores;

    /** Lý do — lấy từ các danh mục đang bật trong {@code watchlist_category}. */
    private List<Option> reasons;

    @Getter
    @Setter
    @ToString
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Option {
        /** Giá trị gửi lại khi tìm kiếm. */
        private String value;
        /** Chữ hiển thị trên dropdown. */
        private String label;
    }
}
