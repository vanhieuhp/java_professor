package dev.hieunv.riskassessment.client;

/**
 * Core lỗi tạm thời — timeout, mất kết nối, 5xx. Gửi lại có khả năng thành công.
 * <p>
 * Phân biệt tạm thời với vĩnh viễn là quyết định quan trọng nhất của tầng client. Thử lại
 * một lỗi vĩnh viễn (ví dụ CIF không tồn tại) chỉ tạo tải vô ích và trì hoãn việc con người
 * biết có chuyện sai; còn KHÔNG thử lại một lỗi tạm thời thì đánh mất kết quả rủi ro chỉ vì
 * mạng chập một giây.
 */
public class CoreTransientException extends RuntimeException {

    public CoreTransientException(String message) {
        super(message);
    }

    public CoreTransientException(String message, Throwable cause) {
        super(message, cause);
    }
}
