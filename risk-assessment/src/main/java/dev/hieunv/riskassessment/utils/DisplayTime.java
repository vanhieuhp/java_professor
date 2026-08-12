package dev.hieunv.riskassessment.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Định dạng thời gian cho báo cáo — {@code dd/MM/yyyy HH:mm:ss}.
 *
 * <h2>Vì sao API trả CẢ hai dạng</h2>
 * {@link Instant} là mốc thời gian, chuỗi đã định dạng là cách đọc mốc đó ở một múi giờ. Trả
 * mỗi chuỗi thì client mất khả năng sắp xếp, so sánh hay đổi múi giờ; trả mỗi {@code Instant}
 * thì mỗi client tự định dạng và sớm muộn hai màn hình hiển thị cùng một bản ghi theo hai kiểu.
 * Trả cả hai: máy đọc trường thứ nhất, người đọc trường thứ hai.
 *
 * <h2>Vì sao chốt múi giờ ở server</h2>
 * Định dạng theo múi giờ của trình duyệt nghĩa là hai kiểm soát viên ngồi hai nước sẽ thấy hai
 * thời điểm khác nhau cho cùng một lần đánh giá — và đây là dữ liệu bị thanh tra.
 */
public final class DisplayTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZONE);

    private DisplayTime() {
    }

    public static String format(Instant instant) {
        return instant == null ? null : FORMATTER.format(instant);
    }
}
