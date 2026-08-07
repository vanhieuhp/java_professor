package dev.hieunv.riskassessment.event;

/**
 * Sự kiện hỏng tới mức thử lại cũng vô ích.
 *
 * <h2>Vì sao phải là một kiểu ngoại lệ riêng</h2>
 * Consumer cần phân biệt hai chuyện hoàn toàn khác nhau:
 * <ul>
 *   <li><b>Lỗi tạm thời</b> (database mất kết nối, deadlock) — <b>không</b> ack, để Kafka
 *       giao lại. Thử lại sẽ thành công.</li>
 *   <li><b>Sự kiện hỏng</b> (JSON sai, thiếu khóa chống trùng) — <b>ack</b> để partition đi
 *       tiếp. Thử lại chỉ làm mọi khách hàng phía sau ngừng được rà soát.</li>
 * </ul>
 * Không phân biệt được thì phải chọn một hướng cho cả hai, và hướng nào cũng sai: ack tất
 * cả là mất sự kiện khi database chớp tắt; không ack tất cả là một gói tin hỏng làm tê liệt
 * một phần ba lưu lượng vĩnh viễn.
 */
public class PoisonEventException extends RuntimeException {

    public PoisonEventException(String message) {
        super(message);
    }

    public PoisonEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
