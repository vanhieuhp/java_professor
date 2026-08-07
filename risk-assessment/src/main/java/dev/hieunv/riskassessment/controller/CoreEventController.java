package dev.hieunv.riskassessment.controller;

import dev.hieunv.riskassessment.event.CoreEventOutcome;
import dev.hieunv.riskassessment.event.CoreEventSource;
import dev.hieunv.riskassessment.event.CustomerChangeProcessor;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import dev.hieunv.riskassessment.event.CustomerChangedEventConsumer;
import dev.hieunv.riskassessment.event.PoisonEventException;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Đường dự phòng cho sự kiện Core, và cửa sổ quan sát tình trạng nhận sự kiện.
 *
 * <h2>Vì sao đường dự phòng đi qua ĐÚNG cái processor của Kafka</h2>
 * Không phải để tiết kiệm code. Bài học đã trả giá ở pcrt-lab: đường dự phòng nhận sự kiện,
 * trả 200, nhưng không chấm điểm — vì rule chỉ được nối vào consumer Kafka. Trên production
 * điều đó nghĩa là mỗi lần chuyển sang đường dự phòng thì toàn bộ rà soát ngừng chạy, đúng
 * lúc Kafka đang có sự cố, và không có gì báo cho ai biết.
 * <p>
 * Hai đường vào cùng một cổng chống trùng, nên bật cả hai cùng lúc là an toàn: cùng một sự
 * kiện đi vào bằng cả hai — chuyện chắc chắn xảy ra khi Core bật dự phòng lúc Kafka mới lag
 * chứ chưa chết hẳn — vẫn chỉ có hiệu ứng một lần.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/pcrt/core-events")
@RequiredArgsConstructor
public class CoreEventController {

    private static final String KEY_REST_FALLBACK = "core.event.rest-fallback.enabled";

    private final CustomerChangeProcessor processor;
    private final CustomerChangedEventConsumer consumer;
    private final PcrtConfigService configService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;

    @PostMapping
    public ResponseEntity<Map<String, Object>> ingest(@RequestBody CustomerChangedEvent event) {
        if (!configService.getBoolean(KEY_REST_FALLBACK, true)) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Đường dự phòng đang tắt (" + KEY_REST_FALLBACK + ")"));
        }
        try {
            CoreEventOutcome outcome = processor.process(
                    event, objectMapper.writeValueAsString(event), CoreEventSource.REST);
            // 200 cho mọi kết cục xử lý được, kể cả DUPLICATE: với người gọi thì kết quả như
            // nhau — thay đổi đã được ghi nhận. Trả 409 sẽ khiến họ tưởng có lỗi và thử lại nữa.
            return ResponseEntity.ok(Map.of(
                    "cif", String.valueOf(event.getCif()),
                    "outcome", outcome.name()));
        } catch (PoisonEventException e) {
            // 400 chứ không 500: lỗi nằm ở gói tin, gửi lại y nguyên cũng vô ích. Mã lỗi phải
            // nói được điều đó, nếu không người gọi sẽ thử lại mãi.
            log.error("Sự kiện REST hỏng cấu trúc: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** Số liệu để nghiệm thu: nhận bao nhiêu, kết cục ra sao, còn bao nhiêu chưa xử lý. */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        CustomerChangedEventConsumer.ConsumerStats c = consumer.stats();
        return ResponseEntity.ok(Map.of(
                "kafkaReceived", c.getReceived(),
                "kafkaPoisoned", c.getPoisoned(),
                // byOutcome bao CẢ Kafka lẫn REST — bộ đếm nằm ở processor. Đếm riêng theo
                // đường vào thì lúc Kafka chết và mọi thứ chạy qua REST, bảng số liệu sẽ
                // hiển thị 0 đúng vào lúc cần nhìn nhất.
                "byOutcome", processor.outcomeCounts(),
                "inboxRows", jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pcrt_core_event_inbox", Long.class),
                "inboxUnprocessed", jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pcrt_core_event_inbox WHERE processed_at IS NULL", Long.class),
                "inboxRejected", jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pcrt_core_event_inbox WHERE process_error IS NOT NULL", Long.class),
                "mirrorTombstones", jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM pcrt_customer_identity WHERE deleted_at IS NOT NULL", Long.class)));
    }

    /**
     * Các sự kiện không qua xác thực, kèm payload thô.
     * <p>
     * Đây là thứ mà "ghi log rồi bỏ" không cho được: một danh sách truy vấn được, để người
     * vận hành biết Core đang gửi sai cái gì và bao nhiêu khách hàng đang không được rà soát.
     */
    @GetMapping("/rejected")
    public ResponseEntity<List<Map<String, Object>>> rejected(
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(jdbcTemplate.queryForList("""
                SELECT id, event_id, cif, change_type, source, received_at, process_error, payload
                FROM pcrt_core_event_inbox
                WHERE process_error IS NOT NULL
                ORDER BY id DESC
                LIMIT ?
                """, limit));
    }
}
