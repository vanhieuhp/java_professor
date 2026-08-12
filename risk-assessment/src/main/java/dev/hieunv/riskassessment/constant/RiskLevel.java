package dev.hieunv.riskassessment.constant;

/**
 * Mức độ rủi ro rửa tiền — spec mục A.6.
 */
public enum RiskLevel {

    HIGH("Cao"),
    MEDIUM("Trung bình");

    /**
     * Chữ hiển thị trên web admin. Tách khỏi tên hằng có chủ ý: tên hằng là giá trị được ghi
     * xuống DB và đi trong contract gửi Core, nên đổi nó là đổi dữ liệu; còn đổi nhãn chỉ là
     * đổi chữ trên màn hình.
     */
    private final String label;

    RiskLevel(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
