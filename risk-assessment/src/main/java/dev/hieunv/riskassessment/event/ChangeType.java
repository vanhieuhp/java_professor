package dev.hieunv.riskassessment.event;


public enum ChangeType {

    /**
     * KH mới hoàn tất eKYC.
     */
    CREATED,

    /**
     * Đổi thông tin định danh: họ tên, ngày sinh, SĐT, giấy tờ, quốc tịch, nghề nghiệp.
     */
    UPDATED,

    /**
     * Chỉ đổi trạng thái ví: ACTIVE ↔ LOCKED ↔ CLOSED. Không đụng tới định danh.
     */
    STATUS_CHANGED,

    /**
     * Core không còn khách hàng này. Bản chiếu phải gỡ ra khỏi tập quét.
     */
    DELETED;

    /**
     * Loại này có cần chấm điểm không, hay chỉ cập nhật bản chiếu rồi thôi.
     */
    public boolean requiresScreening() {
        return this != DELETED;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static ChangeType fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        for (ChangeType type : values()) {
            if (type.name().equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
