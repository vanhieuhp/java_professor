package dev.hieunv.riskassessment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PcrtBatchConfig {

    /**
     * Executor riêng cho batch.
     * <p>
     * Spec ghi "xử lý tuần tự", nên pool chỉ có 1 thread — batch chạy hết khách hàng này
     * mới sang khách hàng khác. Hàng đợi vẫn cho phép xếp nhiều lần quét: nếu DS đen bị
     * chỉnh hai lần liên tiếp, lần quét thứ hai chờ lần thứ nhất xong chứ không chạy chồng
     * lên nhau.
     * <p>
     * Tách khỏi pool xử lý HTTP là điều bắt buộc: một batch 5 triệu khách hàng chiếm hết
     * thread chung sẽ làm API realtime của TH2 chết đói.
     */
    @Bean
    public TaskExecutor pcrtBatchExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("pcrt-batch-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
