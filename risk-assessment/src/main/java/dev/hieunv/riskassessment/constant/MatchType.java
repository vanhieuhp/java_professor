package dev.hieunv.riskassessment.constant;

public enum MatchType {

    /** Định danh cá nhân: GTTT / họ tên / ngày sinh / SĐT. Áp dụng cho DS đen và ưu tiên 1, 3, 7. */
    K1,

    /** Quốc gia trong địa chỉ KH. Áp dụng cho ưu tiên 2, 4, 9. */
    K2,

    /** Nghề nghiệp của KH. Áp dụng cho ưu tiên 5, 8. */
    K3,

    /** Chức vụ của KH. Áp dụng cho ưu tiên 6. */
    K4
}
