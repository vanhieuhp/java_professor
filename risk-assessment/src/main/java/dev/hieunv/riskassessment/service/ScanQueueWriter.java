package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.core.CoreCustomer;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
