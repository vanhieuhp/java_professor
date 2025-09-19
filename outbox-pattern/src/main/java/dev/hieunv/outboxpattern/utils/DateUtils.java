package dev.hieunv.outboxpattern.utils;

import lombok.experimental.UtilityClass;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Date;

@UtilityClass
public class DateUtils {
private static final String DEFAULT_DATE_FORMAT = "dd-MM-yyyy HH:mm:ss";
private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final ThreadLocal<SimpleDateFormat> formatter =
        ThreadLocal.withInitial(() -> new SimpleDateFormat(DEFAULT_DATE_FORMAT));

    /**
     * Format Date thành chuỗi theo định dạng dd-MM-yyyy HH:mm:ss
     */
    public static String format(Date date) {
        if (date == null) return null;
        return formatter.get().format(date);
    }

    /**
     * Format với định dạng custom nếu cần
     */
    public static String format(Date date, String pattern) {
        if (date == null || pattern == null) return null;
        return new SimpleDateFormat(pattern).format(date);
    }

    /**
     * Convert chuỗi thành Date
     */
    public static Date parse(String dateStr) {
        try {
            return formatter.get().parse(dateStr);
        } catch (Exception e) {
            return null;
        }
    }
    public static Date addMinute(Date oldDate, int min) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(oldDate);
        calendar.add(Calendar.MINUTE, min);
        return calendar.getTime();
    }

       public static String formatDate(LocalDateTime dateTime) {
        if (dateTime == null) return null;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(DEFAULT_DATE_FORMAT);
        return dateTime.format(formatter);
    }
     public static LocalDateTime  timeNow() {
        return LocalDateTime.now(VIETNAM_ZONE);
    }
}
