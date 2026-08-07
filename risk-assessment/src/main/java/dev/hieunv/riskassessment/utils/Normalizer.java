package dev.hieunv.riskassessment.utils;

import java.text.Normalizer.Form;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Normalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NON_DIGIT = Pattern.compile("\\D");

    public static String name(String raw) {
        if (raw == null) {
            return null;
        }

        String s = raw.replace('đ', 'd').replace('Đ', 'D');

        // NFD tách nguyên âm khỏi dấu: "ẫ" → "a" + U+0303 + U+0302
        s = java.text.Normalizer.normalize(s, Form.NFD);
        s = COMBINING_MARKS.matcher(s).replaceAll("");

        s = s.toUpperCase(Locale.ROOT);

        s = WHITESPACE.matcher(s).replaceAll(" ").trim();
        return s.isEmpty() ? null : s;
    }

    /**
     * Số điện thoại Việt Nam về dạng nội địa bắt đầu bằng 0.
     *   "+84912345678" | "84912345678" | "0912345678" | "0912 345 678"  →  "0912345678"
     */
    public static String phone(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        boolean hadPlus = trimmed.startsWith("+");
        String digits = NON_DIGIT.matcher(trimmed).replaceAll("");
        if (digits.isEmpty()) {
            return null;
        }

        if (digits.startsWith("0084")) {
            digits = digits.substring(4);
        } else if (hadPlus && digits.startsWith("84")) {
            digits = digits.substring(2);
        } else if (digits.startsWith("84") && digits.length() >= 11) {
            // Không dùng độ dài < 11 để tránh cắt nhầm số nội địa như "0847..."
            digits = digits.substring(2);
        }
        if (digits.startsWith("0")) {
            digits = digits.substring(1);
        }
        return digits.isEmpty() ? null : "0" + digits;
    }

    /**
     * Số giấy tờ tùy thân: bỏ khoảng trắng, viết hoa (hộ chiếu có chữ cái).
     * Không suy diễn giữa CMND 9 số và CCCD 12 số — đó là hai giá trị khác nhau,
     * spec xử lý bằng trường "Số GTTT cũ" riêng (Q3), không bằng phép biến đổi chuỗi.
     */
    public static String idNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = WHITESPACE.matcher(raw).replaceAll("").toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }

    /**
     * Mã danh mục (quốc gia / nghề nghiệp / chức vụ): trim + viết hoa.
     */
    public static String code(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toUpperCase(Locale.ROOT);
        return s.isEmpty() ? null : s;
    }
}
