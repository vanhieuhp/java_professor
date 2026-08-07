package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Trigger của TH1 — phát hiện DS đen được điều chỉnh rồi khởi động lần quét toàn bộ.
 *
 * <h2>Vì sao không phải cron</h2>
 * Spec A.1-T1 ghi "realtime". Một người vừa bị đưa vào danh sách trừng phạt mà hệ thống đợi
 * tới 2 giờ sáng hôm sau mới quét thì suốt ngày hôm đó họ vẫn giao dịch bình thường. Đây là
 * khác biệt bản chất với TH3 (định kỳ, chấp nhận trễ một ngày).
 *
 * <h2>Ở đây mô phỏng bằng polling — thật thì không</h2>
 * Component này hỏi DB mỗi vài giây xem {@code entries_changed_at} của DS đen có đổi không.
 * Hệ thống thật nên là push: màn hình quản trị sửa DS đen thì phát một sự kiện (Kafka, hoặc
 * {@code ApplicationEventPublisher} nếu cùng process). Polling ở đây chỉ để chạy thử được
 * mà không cần dựng message broker — nhưng nó cũng là lưới an toàn thật: nếu ai đó sửa DS
 * đen thẳng dưới DB, đường push sẽ bỏ sót còn đường này thì không.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BlacklistChangeDetector {

    private final WatchlistCategoryRepository categoryRepository;
    private final BatchScanService batchScanService;
    private final PcrtConfigService configService;

    private volatile Instant lastSeen;

    @PostConstruct
    public void captureBaseline() {
        lastSeen = currentMark();
        log.info("Blacklist watermark at startup: {}", lastSeen);
    }

    @Scheduled(fixedDelayString = "${pcrt.blacklist-detector.interval-ms:10000}")
    public void detect() {
        if (!configService.getBoolean(PcrtConfigService.KEY_TH1_AUTO, true)) {
            return;
        }
        Instant mark = currentMark();
        if (Objects.equals(mark, lastSeen)) {
            return;
        }
        log.warn("BLACKLIST CHANGED ({} -> {}) — triggering TH1", lastSeen, mark);
        lastSeen = mark;
        UUID batchId = batchScanService.startBlacklistScan();
        log.warn("TH1 batch {} started", batchId);
    }

    /**
     * Đặt lại mốc về hiện tại mà không kích hoạt quét.
     * <p>
     * Dùng khi có tiến trình khác đã xử lý xong thay đổi của DS đen (ví dụ đường quét ngược,
     * hoặc phép đo đối chứng hai chiều). Không có method này thì lần {@link #detect()} kế
     * tiếp sẽ thấy mốc lệch và quét lại toàn bộ khách hàng một cách vô ích.
     */
    public void resyncBaseline() {
        lastSeen = currentMark();
    }

    private Instant currentMark() {
        return categoryRepository.findBlacklistCategory()
                .map(c -> c.getEntriesChangedAt())
                .orElse(null);
    }
}
