package dev.hieunv.riskassessment.client;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;

/**
 * Cầu dao — ngừng gọi Core khi Core đang chết.
 *
 * <h2>Vì sao retry không đủ</h2>
 * Retry giải quyết trục trặc thoáng qua. Khi Core down thật, retry lại thành tác nhân gây
 * hại: mỗi kết quả rủi ro tốn 3 lần thử × timeout 3 giây = 9 giây chờ vô ích, hàng nghìn
 * kết quả xếp hàng, thread bị giữ, và bản thân Core lúc đang cố hồi phục lại bị dội thêm
 * tải. Cầu dao cắt đứt vòng đó: sau N lần lỗi liên tiếp thì thất bại ngay lập tức, không
 * gọi mạng nữa.
 *
 * <h2>Ba trạng thái</h2>
 * <ul>
 *   <li><b>CLOSED</b> — bình thường, cho gọi. Đếm lỗi liên tiếp.</li>
 *   <li><b>OPEN</b> — chặn mọi lời gọi trong {@code openDuration}. Đây là lúc Core được yên.</li>
 *   <li><b>HALF_OPEN</b> — hết thời gian chờ, cho ĐÚNG MỘT lời gọi đi thử. Thành công thì
 *       đóng lại, thất bại thì mở tiếp một chu kỳ nữa.</li>
 * </ul>
 * Trạng thái HALF_OPEN là điểm mấu chốt: nếu hết thời gian chờ mà thả cả nghìn request đang
 * dồn ứ đi cùng lúc, Core vừa ngóc dậy sẽ bị đánh sập lần nữa.
 *
 * <p>Viết tay thay vì dùng thư viện vì ở quy mô này logic chỉ có vài chục dòng và việc nhìn
 * thấy nó rõ ràng đáng giá hơn. Hệ thống thật nhiều instance thì trạng thái cầu dao nên
 * dùng bản của thư viện (có sliding window, thống kê theo tỉ lệ lỗi thay vì đếm liên tiếp).
 */
@Slf4j
public class SimpleCircuitBreaker {

    public enum State {CLOSED, OPEN, HALF_OPEN}

    private final String name;
    private final int failureThreshold;
    private final Duration openDuration;

    private State state = State.CLOSED;
    private int consecutiveFailures;
    private Instant openedAt;

    public SimpleCircuitBreaker(String name, int failureThreshold, Duration openDuration) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.openDuration = openDuration;
    }

    /** Có được phép gọi không. Tự chuyển OPEN → HALF_OPEN khi đã hết thời gian chờ. */
    public synchronized boolean allowRequest() {
        if (state == State.OPEN) {
            if (Instant.now().isAfter(openedAt.plus(openDuration))) {
                state = State.HALF_OPEN;
                log.warn("Circuit breaker [{}] switched to HALF_OPEN — letting one trial call through", name);
                return true;
            }
            return false;
        }
        // HALF_OPEN: chỉ cho một lời gọi. Lời gọi đó gọi recordSuccess/recordFailure
        // trước khi lời gọi kế tiếp tới, nên không cần đếm thêm ở đây.
        return true;
    }

    public synchronized void recordSuccess() {
        if (state != State.CLOSED) {
            log.warn("Circuit breaker [{}] CLOSED again — Core has recovered", name);
        }
        state = State.CLOSED;
        consecutiveFailures = 0;
    }

    public synchronized void recordFailure() {
        consecutiveFailures++;
        if (state == State.HALF_OPEN || consecutiveFailures >= failureThreshold) {
            state = State.OPEN;
            openedAt = Instant.now();
            log.error("Circuit breaker [{}] OPEN after {} consecutive failures — no Core calls for {}s",
                    name, consecutiveFailures, openDuration.toSeconds());
        }
    }

    public synchronized State state() {
        return state;
    }

    public synchronized int consecutiveFailures() {
        return consecutiveFailures;
    }
}
