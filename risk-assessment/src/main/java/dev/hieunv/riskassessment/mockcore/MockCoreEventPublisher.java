package dev.hieunv.riskassessment.mockcore;

import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * PHÍA CORE VÍ — không phải một phần của PCRT.
 *
 * <h2>Khóa phân vùng là CIF, không phải eventId</h2>
 * Kafka chỉ bảo đảm thứ tự <b>trong một partition</b>. Lấy {@code eventId} làm khóa thì hai
 * thay đổi liên tiếp của cùng một khách hàng rơi vào hai partition khác nhau, hai luồng
 * consumer xử lý song song, và thứ tự trở thành ngẫu nhiên: bản chiếu có thể nhận "đổi tên
 * thành X" sau "đổi tên thành Y" dù Core làm ngược lại.
 * <p>
 * Lấy CIF làm khóa thì mọi sự kiện của một khách hàng luôn cùng partition, luôn đúng thứ tự.
 * Chốt thứ tự {@code core_updated_at} bên PCRT vẫn giữ nguyên — nó là lưới an toàn cho những
 * trường hợp khóa phân vùng không cứu được (Core gửi lại sau lỗi mạng, thay đổi số partition).
 * Nhưng lưới an toàn chỉ nên đỡ trường hợp hiếm, không phải đỡ mọi sự kiện.
 * <p>
 * Cái giá: một khách hàng cực kỳ hoạt động sẽ dồn tải vào một partition. Với sự kiện đổi
 * thông tin KH thì không đáng lo — người ta không đổi CCCD mỗi giây.
 *
 * <h2>Chỗ này là dual-write, và nó chưa được giải</h2>
 * Core cập nhật {@code core.wallet_customer} rồi publish. Hai hệ thống, không có commit chung.
 * Publish hỏng sau khi DB đã commit thì <b>PCRT không bao giờ biết khách hàng này đã đổi</b> —
 * đúng kiểu bỏ sót nguy hiểm nhất, vì không có ngoại lệ nào ném ra ở phía PCRT.
 * <p>
 * Lời giải đúng là outbox <b>bên Core</b>: ghi sự kiện vào một bảng trong cùng transaction
 * với việc cập nhật khách hàng, rồi một job đọc bảng đó bắn sang Kafka. Ở đây không làm vì
 * đó là việc của codebase Core, không phải của PCRT — nhưng phải nói rõ ra, vì nếu không thì
 * lời hứa "không mất sự kiện" của PCRT chỉ đúng từ Kafka trở đi.
 * <p>
 * Lưới đỡ hiện có bên PCRT: job đồng bộ delta và lần quét xuôi hằng đêm vẫn phát hiện được
 * thay đổi bị mất. Chậm hơn nhiều, nhưng không im lặng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pcrt.mock-core.enabled", havingValue = "true", matchIfMissing = true)
public class MockCoreEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${pcrt.topic.customer-changed}")
    private String topic;

    public void publish(CustomerChangedEvent event) {
        String payload = objectMapper.writeValueAsString(event);
        kafkaTemplate.send(topic, event.getCif(), payload);
        log.info("[CORE] publish {} — CIF {} ({})",
                event.getChangeType(), event.getCif(), event.getEventId());
    }

    /** Gửi thẳng chuỗi thô — để dựng được sự kiện hỏng mà không cần lách qua kiểu dữ liệu. */
    public void publishRaw(String key, String rawPayload) {
        kafkaTemplate.send(topic, key, rawPayload);
        log.warn("[CORE] publishing RAW payload with key {} — {}", key, rawPayload);
    }
}
