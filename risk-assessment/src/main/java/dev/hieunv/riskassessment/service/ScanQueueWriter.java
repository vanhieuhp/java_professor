package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Ghi một trang khách hàng vào hàng đợi, mỗi trang một transaction.
 * <p>
 * Phải là bean riêng chứ không phải method của {@link ScanEnqueueService}: Spring áp
 * {@code @Transactional} bằng proxy, nên lời gọi {@code this.savePage(...)} từ trong cùng
 * class sẽ đi thẳng vào method thật và bỏ qua annotation — không có transaction nào được
 * mở, và không có lỗi nào báo ra.
 */
@Service
@RequiredArgsConstructor
public class ScanQueueWriter {

    private final CustomerScanEventRepository scanQueueRepository;

    @Transactional
    public int savePage(UUID batchId, TriggerType triggerType, List<CoreCustomer> page) {
        List<CustomerScanEvent> rows = page.stream()
                .map(c -> ScanEventMapper.fromCore(c, triggerType, batchId))
                .toList();
        scanQueueRepository.saveAll(rows);
        return rows.size();
    }
}
