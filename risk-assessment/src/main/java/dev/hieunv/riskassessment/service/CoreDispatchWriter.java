package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.CoreSendStatus;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Ghi kết quả của một lần gửi Core. Bean riêng để {@code @Transactional} thực sự có hiệu lực
 * (vòng lặp gửi nằm ở {@link CoreDispatchService} và không được bọc transaction).
 */
@Service
@RequiredArgsConstructor
public class CoreDispatchWriter {

    private final CustomerRiskResultRepository riskResultRepository;

    @Transactional
    public void markSent(Long resultId) {
        CustomerRiskResult result = riskResultRepository.findById(resultId).orElseThrow();
        result.setCoreSendStatus(CoreSendStatus.SENT);
        result.setCoreSentAt(Instant.now());
        result.setAttemptCount((short) (result.getAttemptCount() + 1));
        result.setNextAttemptAt(null);
        result.setLastError(null);
        riskResultRepository.save(result);
    }

    /**
     * Backoff mũ giữa các lần chạy job: {@code base * 2^(attempt-1)}.
     * <p>
     * Khác với retry bên trong một lần gọi (mili-giây, cho trục trặc mạng thoáng qua), mốc
     * này tính bằng phút — nó dành cho trường hợp Core thực sự down và cần thời gian để dậy.
     */
    @Transactional
    public void markFailed(Long resultId, String error, int backoffBaseSeconds, boolean retryable) {
        CustomerRiskResult result = riskResultRepository.findById(resultId).orElseThrow();
        short attempt = (short) (result.getAttemptCount() + 1);

        result.setCoreSendStatus(CoreSendStatus.FAILED);
        result.setAttemptCount(attempt);
        result.setLastError(truncate(error));

        if (retryable) {
            long seconds = (long) (backoffBaseSeconds * Math.pow(2, Math.max(0, attempt - 1)));
            result.setNextAttemptAt(Instant.now().plus(Duration.ofSeconds(Math.min(seconds, 3600))));
        } else {
            // Lỗi vĩnh viễn: không hẹn lần sau. Bản ghi nằm lại ở FAILED cho người xử lý,
            // thay vì quay vòng vô ích trong hàng đợi.
            result.setNextAttemptAt(null);
            result.setAttemptCount(Short.MAX_VALUE);
        }
        riskResultRepository.save(result);
    }

    /**
     * Đưa mọi kết quả FAILED trở lại hàng đợi. Dùng sau khi sửa nguyên nhân gốc —
     * thường là cấu hình sai, không phải dữ liệu hỏng.
     */
    @Transactional
    public int requeueFailed() {
        return riskResultRepository.requeueFailed();
    }

    private static String truncate(String s) {
        if (s == null) {
            return null;
        }
        return s.length() <= 500 ? s : s.substring(0, 500);
    }
}
