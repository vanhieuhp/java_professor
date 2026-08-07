package dev.hieunv.riskassessment.event;

import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Xác thực sự kiện trước khi cho vào đường xử lý.
 *
 * <h2>Hai hạng lỗi, hai số phận khác nhau</h2>
 * <ul>
 *   <li><b>Lỗi cấu trúc</b> — thiếu {@code eventId}, {@code cif} hoặc {@code changeType}.
 *       Không ghi nổi vào inbox vì chính ba cột đó là NOT NULL, và không có {@code eventId}
 *       thì cũng không chống trùng được. Chỉ còn cách ghi log rồi bỏ.</li>
 *   <li><b>Lỗi nghiệp vụ</b> — đủ khóa nhưng dữ liệu sai (ngày sinh ở tương lai, trạng thái
 *       lạ, thiếu họ tên). Ghi được vào inbox kèm lý do, {@code processed_at} để NULL. Truy
 *       vấn được, chạy lại được sau khi Core sửa nguồn.</li>
 * </ul>
 * Phân biệt này quan trọng hơn vẻ ngoài của nó. Gộp cả hai vào "log rồi bỏ" thì một lỗi tích
 * hợp ở phía Core sẽ âm thầm loại hàng nghìn khách hàng khỏi diện rà soát, và bằng chứng duy
 * nhất nằm trong file log — thứ sẽ bị xoay vòng sau vài ngày.
 *
 * <h2>Vì sao lỗi xác thực KHÔNG được thử lại</h2>
 * Một sự kiện thiếu họ tên sẽ thiếu họ tên ở lần thử thứ một nghìn. Thử lại chỉ làm partition
 * đứng im, và mọi khách hàng phía sau trong partition đó ngừng được rà soát — một lỗi dữ
 * liệu của MỘT khách hàng làm tê liệt cả một phần ba lưu lượng.
 */
@Component
public class CustomerChangeValidator {

    private static final Set<String> KNOWN_STATUSES = Set.of("ACTIVE", "APPROVED", "LOCKED", "CLOSED");
    private static final Set<String> KNOWN_CUSTOMER_TYPES = Set.of("CN", "TC");

    /** Sớm hơn mốc này thì gần như chắc chắn là lỗi nhập liệu, không phải cụ già 130 tuổi. */
    private static final LocalDate EARLIEST_PLAUSIBLE_DOB = LocalDate.of(1900, 1, 1);

    /** Lệch đồng hồ giữa hai máy chủ là bình thường; lệch quá mức này thì là lỗi cấu hình. */
    private static final long CLOCK_SKEW_TOLERANCE_SECONDS = 300;

    public Verdict validate(CustomerChangedEvent e) {
        List<String> structural = new ArrayList<>();
        if (isBlank(e.getEventId())) {
            structural.add("thiếu eventId — không chống trùng được");
        }
        if (isBlank(e.getCif())) {
            structural.add("thiếu cif");
        }
        if (e.getChangeType() == null) {
            structural.add("thiếu hoặc không nhận dạng được changeType");
        }
        if (!structural.isEmpty()) {
            return Verdict.builder().structural(true).problems(structural).build();
        }

        List<String> problems = new ArrayList<>();

        // occurredAt là mốc thứ tự của bản chiếu. Thiếu nó thì không biết sự kiện này mới hay
        // cũ hơn dữ liệu đang có, và chốt thứ tự mất tác dụng — nguy hiểm hơn là bỏ qua.
        if (e.getOccurredAt() == null) {
            problems.add("thiếu occurredAt — không xác định được thứ tự so với dữ liệu hiện có");
        } else if (e.getOccurredAt().isAfter(Instant.now().plusSeconds(CLOCK_SKEW_TOLERANCE_SECONDS))) {
            // KHÔNG chặn: câu UPSERT đã có LEAST(..., now()) cắt mốc tương lai về hiện tại.
            // Nhưng phải nêu ra, vì cắt trong im lặng thì lỗi đồng hồ bên Core không bao giờ
            // lộ, và nó sẽ lộ theo cách khác — bằng những dòng bản chiếu bị khóa cứng.
            problems.add("occurredAt " + e.getOccurredAt() + " nằm ở tương lai — kiểm tra đồng hồ bên Core");
        }

        if (e.getStatus() != null && !KNOWN_STATUSES.contains(e.getStatus())) {
            // Trạng thái lạ quyết định sai scan_target. Đoán bừa theo hướng nào cũng sai được:
            // coi là quét thì rà soát người đã đóng ví, coi là không quét thì bỏ sót người
            // đang hoạt động. Chặn lại để có người nhìn.
            problems.add("status '" + e.getStatus() + "' không thuộc " + KNOWN_STATUSES);
        }
        if (e.getCustomerType() != null && !KNOWN_CUSTOMER_TYPES.contains(e.getCustomerType())) {
            problems.add("customerType '" + e.getCustomerType() + "' không thuộc " + KNOWN_CUSTOMER_TYPES);
        }

        if (e.getChangeType().requiresScreening()) {
            validateIdentity(e, problems);
        }

        return Verdict.builder().structural(false).problems(problems).build();
    }

    /**
     * Chỉ chạy cho các loại cần chấm điểm. Sự kiện DELETED không cần họ tên hay ngày sinh —
     * bắt buộc chúng ở đó sẽ ép Core phải gửi kèm dữ liệu của một khách hàng nó vừa xóa.
     */
    private void validateIdentity(CustomerChangedEvent e, List<String> problems) {
        if (isBlank(e.getFullName())) {
            problems.add("thiếu fullName — luật K1 cần trường này");
        }
        if (e.getDob() == null) {
            problems.add("thiếu dob — luật K1 cần trường này");
        } else if (e.getDob().isAfter(LocalDate.now())) {
            problems.add("dob " + e.getDob() + " ở tương lai");
        } else if (e.getDob().isBefore(EARLIEST_PLAUSIBLE_DOB)) {
            problems.add("dob " + e.getDob() + " sớm bất thường — nhiều khả năng lỗi nhập liệu");
        }

        // Số GTTT và SĐT KHÔNG bắt buộc. Luật K1 trùng khi khớp ≥2 trong 4 trường, nên thiếu
        // một trường vẫn so khớp được bằng ba trường còn lại. Bắt buộc chúng ở đây là tự
        // dựng thêm một cách để bỏ sót: một khách hàng thiếu số điện thoại sẽ bị chặn ở cổng
        // thay vì được rà soát bằng tên + ngày sinh.
        if (isBlank(e.getIdNumber()) && isBlank(e.getPhone())) {
            problems.add("thiếu cả idNumber lẫn phone — chỉ còn fullName + dob để so khớp");
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Builder
    @Getter
    public static class Verdict {
        /** true = hỏng tới mức không ghi nổi vào inbox. */
        private final boolean structural;
        private final List<String> problems;

        public boolean isValid() {
            return problems.isEmpty();
        }

        public String describe() {
            return String.join("; ", problems);
        }
    }
}
