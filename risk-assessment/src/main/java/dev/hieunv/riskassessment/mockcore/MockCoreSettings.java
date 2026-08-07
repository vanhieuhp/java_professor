package dev.hieunv.riskassessment.mockcore;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Công tắc mô phỏng sự cố của Core giả.
 * <p>
 * Không thể kiểm chứng retry, cầu dao hay backoff nếu Core luôn trả lời đúng. Ba núm vặn
 * dưới đây tái tạo ba kiểu hỏng khác nhau, và mỗi kiểu kích hoạt một lớp phòng vệ khác nhau.
 */
@Getter
@Setter
@ToString
@Component
@ConditionalOnProperty(name = "pcrt.mock-core.enabled", havingValue = "true", matchIfMissing = true)
public class MockCoreSettings {

    /** Core chết hoàn toàn — luôn trả 503. Kích hoạt cầu dao. */
    private boolean down = false;

    /** Tỉ lệ lỗi 5xx ngẫu nhiên 0..1 — mô phỏng chập chờn. Kích hoạt retry. */
    private double failureRate = 0.0;

    /** Độ trễ mỗi request (ms). Vượt read-timeout 3s thì thành timeout phía client. */
    private long delayMs = 0;

    /** Trả 400 — lỗi vĩnh viễn, không được thử lại. */
    private boolean rejectAll = false;

    public boolean shouldFailRandomly() {
        return failureRate > 0 && ThreadLocalRandom.current().nextDouble() < failureRate;
    }
}
