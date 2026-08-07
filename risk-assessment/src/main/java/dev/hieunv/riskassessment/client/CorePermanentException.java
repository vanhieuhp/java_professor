package dev.hieunv.riskassessment.client;

/**
 * Core từ chối lệnh vì bản thân lệnh sai — 4xx. Gửi lại bao nhiêu lần cũng vẫn hỏng.
 * <p>
 * Không thử lại. Đánh dấu FAILED ngay để con người nhìn thấy, thay vì để nó âm thầm quay
 * vòng trong hàng đợi.
 */
public class CorePermanentException extends RuntimeException {

    public CorePermanentException(String message) {
        super(message);
    }
}
