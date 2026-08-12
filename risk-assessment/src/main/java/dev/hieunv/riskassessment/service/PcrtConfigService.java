package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.entity.PcrtConfig;
import dev.hieunv.riskassessment.repository.PcrtConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Đọc tham số vận hành từ bảng {@code pcrt_config}.
 * <p>
 * Không cache: các tham số này bị đọc rất thưa (mỗi lần lập lịch, mỗi lần bắt đầu batch),
 * còn việc <b>đổi giá trị phải có hiệu lực ngay</b> mới là thứ quan trọng — đó chính là lý
 * do spec bắt để chúng trong CSDL thay vì file cấu hình.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PcrtConfigService {

    public static final String KEY_TH3_CRON = "th3.scan.cron";
    public static final String KEY_PAGE_SIZE = "batch.page.size";
    public static final String KEY_TH1_AUTO = "th1.auto.trigger.enabled";
    public static final String KEY_RESUME_ON_STARTUP = "batch.resume.on.startup";

    private final PcrtConfigRepository configRepository;

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return configRepository.findById(key)
                .map(PcrtConfig::getConfigValue)
                .orElse(defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String raw = get(key, null);
        if (raw == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Config {} = '{}' is not a number, using default {}", key, raw, defaultValue);
            return defaultValue;
        }
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        String raw = get(key, null);
        return raw == null ? defaultValue : Boolean.parseBoolean(raw.trim());
    }

    public int pageSize() {
        return getInt(KEY_PAGE_SIZE, 1000);
    }

    @Transactional
    public int set(String key, String value) {
        return configRepository.updateValue(key, value);
    }
}
