package dev.hieunv.riskassessment.constant;

/**
 * Trạng thái bản ghi trong hàng đợi đánh giá — spec A.3-B2/B3, A.4-B2, A.5-B2/B3.
 */
public enum ScanStatus {

    /** Chờ xử lý. */
    CXL,

    /** Đã xử lý. */
    DA_XU_LY
}
