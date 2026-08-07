package dev.hieunv.riskassessment.matching;

/**
 * Các trường có thể trùng khi so khớp — spec A.6, A.7.
 * <p>
 * Bốn trường đầu thuộc tiêu chí K1. {@link #ID_NUMBER} đứng riêng một hạng: trùng nó là đủ
 * để kết luận match. Ba trường còn lại phải gộp ít nhất 2 cái, và phải cùng trỏ về MỘT bản ghi.
 * <p>
 * Ba trường cuối thuộc K2/K3/K4 — mỗi tiêu chí chỉ so khớp đúng một trường.
 */
public enum MatchField {

    // K1
    ID_NUMBER,
    FULL_NAME,
    DOB,
    PHONE,

    // K2 / K3 / K4
    COUNTRY,
    OCCUPATION,
    POSITION
}
