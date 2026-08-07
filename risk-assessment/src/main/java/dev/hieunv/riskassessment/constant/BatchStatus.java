package dev.hieunv.riskassessment.constant;

/** Trạng thái một lần quét. */
public enum BatchStatus {

    /** Đang đọc Core và ghi vào hàng đợi — bước B1/B2. */
    ENQUEUING,

    /** Đang xử lý tuần tự hàng đợi — bước B3/B5. */
    PROCESSING,

    COMPLETED,

    FAILED
}
