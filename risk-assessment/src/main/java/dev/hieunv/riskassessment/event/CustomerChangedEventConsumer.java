package dev.hieunv.riskassessment.event;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.atomic.LongAdder;

/**
 * Consumer của topic {@code core.customer.changed}.
 *
 * <h2>Thứ tự: xử lý xong rồi mới ack</h2>
 * {@code enable-auto-commit=false} + {@code ack-mode=manual_immediate} + gọi
 * {@link Acknowledgment#acknowledge()} ở dòng cuối, <b>ngoài</b> khối try. Ba thứ đó nói cùng
 * một điều: offset chỉ tiến khi transaction đã commit.
 * <p>
 * Với auto-commit, Kafka đẩy offset theo đồng hồ (mặc định 5 giây) bất kể xử lý tới đâu.
 * Consumer chết ngay sau một lần auto-commit sẽ mất toàn bộ sự kiện đã nhận nhưng chưa ghi
 * xong — những khách hàng đó không bao giờ được rà soát, và không có gì báo cho ai biết.
 *
 * <h2>Ack rồi vẫn có thể nhận trùng — và đó là chuyện bình thường</h2>
 * Ngược lại, chết SAU khi commit transaction nhưng TRƯỚC khi ack thì Kafka giao lại sự kiện.
 * Không có cách nào bịt khoảng trống này — Postgres và Kafka là hai hệ thống, không có commit
 * chung. Nên hệ thống không cố tránh việc nhận trùng, mà làm cho nhận trùng <b>vô hại</b>:
 * lần thứ hai chạm vào {@code uq_core_inbox_event} và trả về {@link CoreEventOutcome#DUPLICATE}.
 * <p>
 * Đó chính là câu trả lời cho "Kafka có exactly-once không": không, và cũng không cần.
 * Cần at-least-once ở tầng giao nhận cộng với idempotent ở tầng hiệu ứng.
 *
 * <h2>Vì sao ngoại lệ thường KHÔNG được bắt</h2>
 * Chỉ {@link PoisonEventException} và lỗi giải mã JSON mới bị nuốt. Mọi ngoại lệ khác —
 * database mất kết nối, deadlock, timeout — được để bay lên: consumer không ack, Kafka giao
 * lại, và lần sau thành công. Bắt hết rồi ack là biến một sự cố hạ tầng thoáng qua thành mất
 * dữ liệu vĩnh viễn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CustomerChangedEventConsumer {

    private final CustomerChangeProcessor processor;
    private final ObjectMapper objectMapper;

    private final LongAdder received = new LongAdder();
    private final LongAdder poisoned = new LongAdder();
    // Kết cục xử lý được đếm ở CustomerChangeProcessor, không phải ở đây — đường REST dự
    // phòng cũng phải vào cùng bộ đếm. Ở lại đây chỉ có hai số của riêng Kafka.

    @KafkaListener(topics = "${pcrt.topic.customer-changed}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void onCustomerChanged(ConsumerRecord<String, String> record, Acknowledgment ack) {
        received.increment();

        CustomerChangedEvent event;
        try {
            event = objectMapper.readValue(record.value(), CustomerChangedEvent.class);
        } catch (JacksonException e) {
            // JSON hỏng sẽ hỏng y hệt ở lần thử thứ một nghìn. Ack để partition đi tiếp —
            // giữ lại nghĩa là mọi khách hàng phía sau trong partition đó ngừng được rà soát.
            skipPoison(record, ack, e.getMessage());
            return;
        }

        try {
            processor.process(event, record.value(), CoreEventSource.KAFKA);
        } catch (PoisonEventException e) {
            skipPoison(record, ack, e.getMessage());
            return;
        }
        // KHÔNG ack trong khối try: ack phải nằm sau mọi khả năng ném ngoại lệ, nếu không một
        // lỗi ghi database sẽ vừa rollback dữ liệu vừa đẩy offset đi — sự kiện biến mất.
        ack.acknowledge();
    }

    /**
     * Bỏ qua sự kiện hỏng cấu trúc, nhưng ghi lại NGUYÊN VĂN payload.
     * <p>
     * Đây là nhóm duy nhất không ghi được vào inbox (thiếu chính các cột NOT NULL của nó),
     * nên log là nơi lưu trữ duy nhất. Món nợ có ý thức: cần một bảng DLQ để nhóm này cũng
     * truy vấn và chạy lại được. Ghi thiếu payload thì món nợ đó thành không trả được.
     */
    private void skipPoison(ConsumerRecord<String, String> record, Acknowledgment ack, String reason) {
        poisoned.increment();
        log.error("POISON EVENT skipped — topic={} partition={} offset={} key={} reason={} payload={}",
                record.topic(), record.partition(), record.offset(), record.key(), reason, record.value());
        ack.acknowledge();
    }

    public ConsumerStats stats() {
        return ConsumerStats.builder()
                .received(received.sum())
                .poisoned(poisoned.sum())
                .build();
    }

    @Builder
    @Getter
    public static class ConsumerStats {
        private final long received;
        private final long poisoned;
    }
}
