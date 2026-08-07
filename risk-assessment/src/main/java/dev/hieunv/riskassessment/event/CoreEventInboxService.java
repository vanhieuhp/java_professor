package dev.hieunv.riskassessment.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Cổng vào duy nhất của sự kiện Core — nơi lời hứa "chỉ một lần về mặt hiệu ứng" được giữ.
 *
 * <h2>Vì sao {@code ON CONFLICT DO NOTHING RETURNING} chứ không SELECT rồi INSERT</h2>
 * Đọc rồi ghi là hai thao tác, và giữa chúng có một khoảng trống. Hai consumer cùng nhận một
 * sự kiện — chuyện bình thường khi consumer group rebalance — sẽ cùng SELECT thấy chưa có,
 * cùng kết luận "mới", rồi cùng INSERT. Một bên nhận lỗi khóa trùng, nhưng bên kia đã kịp
 * chấm điểm và ghi kết quả.
 * <p>
 * {@code ON CONFLICT} đẩy phép kiểm tra và phép ghi vào <b>một câu lệnh</b>, để database phân
 * xử bằng chính unique index. {@code RETURNING} là mấu chốt còn lại: nó cho biết dòng có thực
 * sự được chèn hay không — thiếu nó thì {@code DO NOTHING} thành công im lặng và không phân
 * biệt được "vừa chèn" với "đã có sẵn".
 *
 * <h2>Tấm vé và hiệu ứng phải cùng một transaction</h2>
 * Dòng inbox chính là tấm vé chống trùng: nó tồn tại nghĩa là "sự kiện này đã xử lý, lần sau
 * bỏ qua". Nếu nó commit riêng và việc chấm điểm hỏng sau đó, tấm vé vẫn còn — mọi lần gửi
 * lại đều bị chặn ở cổng, và <b>khách hàng đó không bao giờ được rà soát</b>, im lặng.
 * <p>
 * Ở PCRT điều này làm được vì hiệu ứng cũng chỉ là ghi Postgres (bản chiếu, hàng đợi, kết
 * quả). Lời gọi sang Core nằm ở một job khác đọc bảng {@code customer_risk_result}, nên
 * không có lời gọi mạng nào trong transaction. Ở pcrt-lab thì hiệu ứng nằm trong Redis nên
 * transaction không bao trọn được và hai mốc phải tách ra — khác biệt đó là do bản chất hiệu
 * ứng, không phải do sở thích.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoreEventInboxService {

    public static final String EVENT_TYPE = "CUSTOMER_CHANGED";

    private static final String CLAIM = """
            INSERT INTO pcrt_core_event_inbox (event_type, event_id, cif, change_type, source, payload)
            VALUES (?, ?, ?, ?, ?, ?::jsonb)
            ON CONFLICT (event_type, event_id) DO NOTHING
            RETURNING id
            """;

    private final JdbcTemplate jdbcTemplate;

    /**
     * Giành quyền xử lý sự kiện.
     *
     * @return true nếu đây là lần đầu thấy nó; false nếu đã có dòng trong inbox
     */
    public boolean claim(CustomerChangedEvent e, String rawPayload, CoreEventSource source) {
        List<Long> inserted = jdbcTemplate.queryForList(CLAIM, Long.class,
                EVENT_TYPE, e.getEventId(), e.getCif(), e.getChangeType().name(),
                source.name(), rawPayload);
        return !inserted.isEmpty();
    }

    /**
     * Đóng sổ sự kiện. Gọi trong cùng transaction với hiệu ứng — hỏng chỗ nào thì cả tấm vé
     * lẫn hiệu ứng cùng rollback, và lần giao lại sau của Kafka sẽ làm lại từ đầu.
     */
    public void markProcessed(String eventId) {
        markProcessed(eventId, null);
    }

    /**
     * Đóng sổ nhưng để lại ghi chú.
     *
     * <h2>Vì sao {@link CoreEventOutcome#STALE} cần đường này</h2>
     * Sự kiện cũ đã được xử lý đúng (bị chốt thứ tự chặn) nên <b>không</b> được giao lại —
     * lần sau vẫn cũ, thử lại vô ích. Nhưng đóng sổ sạch sẽ thì nó biến mất khỏi mọi truy vấn.
     * <p>
     * Chuyện đó nguy hiểm ở quy mô: nếu đồng hồ bên Core chạy lùi, <b>toàn bộ</b> sự kiện sẽ
     * thành STALE và không khách hàng nào được rà soát nữa — trong khi mọi con số đều xanh,
     * inbox không có dòng nào tồn, không có lỗi nào được ném. Ghi chú lại là thứ duy nhất
     * biến sự cố im lặng đó thành một câu truy vấn đếm được.
     */
    public void markProcessed(String eventId, String note) {
        jdbcTemplate.update("""
                UPDATE pcrt_core_event_inbox SET processed_at = now(), process_error = ?
                WHERE event_type = ? AND event_id = ? AND processed_at IS NULL
                """, truncate(note), EVENT_TYPE, eventId);
    }

    /**
     * Sự kiện không qua xác thực: giữ nguyên {@code processed_at} NULL và ghi lý do.
     * <p>
     * Đây là điểm khác quan trọng so với "ghi log rồi bỏ". Dòng inbox vẫn còn, nên truy vấn
     * được ({@code WHERE processed_at IS NULL}), đếm được, và chạy lại được sau khi Core sửa
     * nguồn. Payload thô nằm ngay trong bảng nên không phụ thuộc vào việc file log còn hay
     * đã bị xoay vòng.
     */
    public void markRejected(String eventId, String error) {
        jdbcTemplate.update("""
                UPDATE pcrt_core_event_inbox SET process_error = ?
                WHERE event_type = ? AND event_id = ?
                """, truncate(error), EVENT_TYPE, eventId);
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 1000 ? s : s.substring(0, 1000);
    }
}
