package dev.hieunv.riskassessment.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Q9 — khi service khởi động lại, tiếp tục các lần quét còn dở thay vì bỏ chúng treo.
 * <p>
 * Có thể làm được điều này vì tiến độ nằm hoàn toàn trong DB: hàng đợi đã snapshot đủ dữ
 * liệu khách hàng, nên không cần đọc lại Core; trạng thái từng bản ghi là 'Chờ xử lý' hoặc
 * 'Đã xử lý', nên chạy lại chỉ nhặt phần chưa xong. Không có bước nào phụ thuộc vào biến
 * trong bộ nhớ của tiến trình đã chết.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchResumeRunner implements ApplicationRunner {

    private final BatchScanService batchScanService;
    private final PcrtConfigService configService;

    @Override
    public void run(ApplicationArguments args) {
        if (!configService.getBoolean(PcrtConfigService.KEY_RESUME_ON_STARTUP, true)) {
            return;
        }
        List<UUID> resumed = batchScanService.resumeUnfinished();
        if (resumed.isEmpty()) {
            log.info("Không có lần quét nào còn dở");
        } else {
            log.warn("Đã tiếp tục {} lần quét còn dở: {}", resumed.size(), resumed);
        }
    }
}
