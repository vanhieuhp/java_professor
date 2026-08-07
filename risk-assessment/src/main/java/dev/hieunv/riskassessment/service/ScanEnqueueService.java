package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.repository.CoreCustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.LongFunction;

/**
 * Bước B1 + B2: đọc Core ví, ghi vào hàng đợi trạng thái Chờ xử lý.
 *
 * <h2>Vì sao đọc theo trang, không đọc hết một lần</h2>
 * "Toàn bộ KH Active/Approved" có thể là 5 triệu dòng. Nạp hết vào một {@code List} là vài
 * GB heap và một OutOfMemoryError. Đọc theo trang bằng keyset cursor giữ bộ nhớ ở mức hằng
 * số bất kể tập dữ liệu lớn cỡ nào.
 *
 * <h2>Vì sao mỗi trang là một transaction</h2>
 * Gói cả 5 triệu dòng vào một transaction là một transaction sống nhiều giờ: giữ khóa,
 * chặn autovacuum, phình WAL, và mất trắng toàn bộ nếu dòng cuối cùng lỗi. Mỗi trang commit
 * riêng thì tiến độ được giữ lại, và job chết giữa chừng chỉ mất tối đa một trang.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScanEnqueueService {

    private final CoreCustomerRepository coreCustomerRepository;
    private final ScanQueueWriter scanQueueWriter;

    /** TH1 và TH3a — toàn bộ KH cá nhân Active/Approved. */
    public int enqueueAllActive(UUID batchId, TriggerType triggerType, int pageSize) {
        return enqueue(batchId, triggerType, pageSize,
                afterId -> coreCustomerRepository.findScanTargetsAfter(afterId, pageSize));
    }

    /** TH3b — chỉ KH tạo mới/cập nhật trong ngày liền trước, và điểm khác 7. */
    public int enqueueChangedYesterday(UUID batchId, TriggerType triggerType, int pageSize,
                                       Instant from, Instant to) {
        return enqueue(batchId, triggerType, pageSize,
                afterId -> coreCustomerRepository.findChangedScanTargetsAfter(afterId, from, to, pageSize));
    }

    private int enqueue(UUID batchId, TriggerType triggerType, int pageSize,
                        LongFunction<List<CoreCustomer>> pageReader) {
        long cursor = 0L;
        int total = 0;

        while (true) {
            List<CoreCustomer> page = pageReader.apply(cursor);
            if (page.isEmpty()) {
                break;
            }
            total += scanQueueWriter.savePage(batchId, triggerType, page);
            // Con trỏ là id của dòng cuối trang — trang sau bắt đầu ngay sau nó.
            // Đây là thứ OFFSET không có: giá trị này lưu lại được và không trượt khi
            // dữ liệu nguồn thay đổi giữa hai trang.
            cursor = page.get(page.size() - 1).getId();
        }
        log.info("Batch {} — enqueued {} customers ({})", batchId, total, triggerType);
        return total;
    }
}
