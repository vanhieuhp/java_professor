package dev.hieunv.riskassessment.event;

/**
 * Kết cục của một sự kiện. Đủ chi tiết để đọc số đếm là biết hệ thống đang làm gì.
 * <p>
 * Gộp tất cả thành "thành công / thất bại" sẽ giấu mất trường hợp nguy hiểm nhất:
 * {@link #MIRRORED_ONLY} tăng vọt nghĩa là hàng loạt khách hàng đang bị coi là ngoài tập
 * quét — có thể đúng (một đợt đóng ví), có thể là Core gửi sai trạng thái. Nhìn vào một
 * con số "thành công" thì hai chuyện đó giống hệt nhau.
 */
public enum CoreEventOutcome {

    /** Đã chạy đủ TH2: ghi bản chiếu, vào hàng đợi, so khớp DS đen, ghi kết quả. */
    SCREENED,

    /** Chỉ cập nhật bản chiếu. KH không thuộc tập quét (đã khóa/đóng ví, hoặc là tổ chức). */
    MIRRORED_ONLY,

    /** DELETED — đã đặt bia mộ, gỡ khỏi tập quét. */
    RETIRED,

    /**
     * Sự kiện chở dữ liệu CŨ hơn dữ liệu bản chiếu đang giữ — chốt thứ tự đã chặn lại.
     * <p>
     * Đây là lý do phải có mã riêng thay vì coi như thành công: nếu vẫn chấm điểm bằng dữ
     * liệu cũ đó, kết quả sinh ra sẽ mang cờ {@code is_latest} và <b>đè lên</b> kết quả mới
     * hơn. Một sự kiện gửi lại sau lỗi mạng sẽ lặng lẽ kéo lùi hồ sơ rủi ro của khách hàng.
     */
    STALE,

    /** Đã xử lý ở lần nhận trước. Không làm gì thêm. */
    DUPLICATE,

    /** Không qua xác thực. Đã ghi inbox kèm lý do, chờ người xem. */
    REJECTED
}
