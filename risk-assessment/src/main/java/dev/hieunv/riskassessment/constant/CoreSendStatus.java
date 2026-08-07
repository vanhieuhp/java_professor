package dev.hieunv.riskassessment.constant;

/**
 * Trạng thái gửi kết quả sang Core ví.
 * <p>
 * Kết quả rủi ro được ghi vào DB PCRT TRƯỚC, gửi Core SAU và ngoài transaction. Nếu gộp
 * lời gọi mạng vào trong transaction, một lần Core treo sẽ giữ khóa DB suốt thời gian
 * timeout — và nếu transaction rollback sau khi Core đã nhận, hai bên lệch nhau vĩnh viễn.
 */
public enum CoreSendStatus {

    PENDING,
    SENT,
    FAILED
}
