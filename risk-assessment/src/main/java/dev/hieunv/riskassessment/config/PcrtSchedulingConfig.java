package dev.hieunv.riskassessment.config;

import dev.hieunv.riskassessment.service.BatchScanService;
import dev.hieunv.riskassessment.service.CoreDispatchService;
import dev.hieunv.riskassessment.service.PcrtConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.time.Instant;

/**
 * Lịch chạy đọc từ DB, không hardcode.
 *
 * <h2>Vì sao không dùng {@code @Scheduled(cron = "...")}</h2>
 * Spec A.5-B1 nói giờ chạy TH3 là "x giờ, x cấu hình trong CSDL PCRT". {@code @Scheduled}
 * nhận hằng số biên dịch cứng vào bytecode; đổi giờ chạy sẽ thành sửa code, build, deploy.
 * {@link SchedulingConfigurer} cho phép đăng ký trigger tự tính thời điểm kế tiếp — và vì
 * cấu hình được đọc lại ở MỖI lần tính, sửa một dòng trong {@code pcrt_config} là lần chạy
 * kế tiếp đã theo lịch mới, không cần restart.
 *
 * <h2>Vì sao {@link ObjectProvider}</h2>
 * Class cấu hình được khởi tạo rất sớm. Tiêm thẳng service vào constructor sẽ kéo cả nhánh
 * bean (repository, EntityManager) lên trước khi chúng sẵn sàng. {@code ObjectProvider} hoãn
 * việc lấy bean tới lúc trigger thực sự chạy.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class PcrtSchedulingConfig implements SchedulingConfigurer {

    private static final String DEFAULT_CRON = "0 0 2 * * *";

    private final ObjectProvider<PcrtConfigService> configServiceProvider;
    private final ObjectProvider<BatchScanService> batchScanServiceProvider;
    private final ObjectProvider<CoreDispatchService> coreDispatchServiceProvider;

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registerPeriodicScan(registrar);
        registerCoreDispatch(registrar);
    }

    /** TH3 — đánh giá định kỳ, theo cron đọc từ {@code pcrt_config}. */
    private void registerPeriodicScan(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                () -> batchScanServiceProvider.getObject().startPeriodicScan(),
                context -> {
                    String cron = configServiceProvider.getObject()
                            .get(PcrtConfigService.KEY_TH3_CRON, DEFAULT_CRON);
                    try {
                        Instant next = new CronTrigger(cron).nextExecution(context);
                        log.info("TH3 — cron '{}', next run {}", cron, next);
                        return next;
                    } catch (IllegalArgumentException e) {
                        log.error("Cron '{}' is invalid, falling back to default '{}'", cron, DEFAULT_CRON);
                        return new CronTrigger(DEFAULT_CRON).nextExecution(context);
                    }
                });
    }

    /**
     * Job gửi kết quả sang Core.
     * <p>
     * Mốc kế tiếp tính từ {@code lastCompletion} chứ không phải giờ bắt đầu — tức fixed-delay
     * chứ không phải fixed-rate. Với fixed-rate, một lượt gửi kéo dài 30 giây (Core chậm) sẽ
     * khiến các lượt sau xếp chồng và cùng dội vào một Core vốn đã yếu.
     */
    private void registerCoreDispatch(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                () -> coreDispatchServiceProvider.getObject().dispatchDue(),
                context -> {
                    long intervalMs = configServiceProvider.getObject()
                            .getInt("core.dispatch.interval-ms", 5000);
                    Instant last = context.lastCompletion() != null
                            ? context.lastCompletion()
                            : Instant.now();
                    return last.plusMillis(intervalMs);
                });
    }
}
