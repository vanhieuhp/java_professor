package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.core.CoreCustomer;
import dev.hieunv.riskassessment.core.service.CoreCustomerService;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.service.ScanEnqueueService;
import dev.hieunv.riskassessment.service.ScanQueueWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanEnqueueServiceImpl implements ScanEnqueueService {

    /**
     * Điểm 7 là mức cao nhất và là mức duy nhất kéo theo quy trình khóa CIF.
     */
    private static final short LOCKING_SCORE = 7;

    private static final String CURSOR_START = "";

    private final CoreCustomerService coreCustomerService;
    private final CustomerRiskResultRepository riskResultRepository;
    private final ScanQueueWriter scanQueueWriter;

    /**
     * TH1 và TH3a — toàn bộ KH cá nhân thuộc diện quét.
     */
    @Override
    public int enqueueAllActive(UUID batchId, TriggerType triggerType, int pageSize) {
        return enqueue(batchId, triggerType,
                afterId -> coreCustomerService.nextScanTargetPage(afterId, pageSize),
                false);
    }

    @Override
    public int enqueueChangedYesterday(UUID batchId, TriggerType triggerType, int pageSize,
                                       Instant from, Instant to) {
        return enqueue(batchId, triggerType,
                afterId -> coreCustomerService.nextEnrolledBetweenPage(from, to, afterId, pageSize),
                true);
    }

    private int enqueue(UUID batchId, TriggerType triggerType,
                        Function<String, List<CoreCustomer>> pageReader,
                        boolean skipLockedScore) {
        String cursor = CURSOR_START;
        int total = 0;
        int skipped = 0;

        while (true) {
            List<CoreCustomer> page = pageReader.apply(cursor);
            if (page.isEmpty()) {
                break;
            }
            cursor = page.getLast().getCif();

            List<CoreCustomer> selected = skipLockedScore ? withoutLockingScore(page) : page;
            skipped += page.size() - selected.size();
            total += scanQueueWriter.savePage(batchId, triggerType, selected);
        }

        if (skipped > 0) {
            log.info("Batch {} — skipped {} customers already holding score {}", batchId, skipped, LOCKING_SCORE);
        }
        log.info("Batch {} — enqueued {} customers ({})", batchId, total, triggerType);
        return total;
    }

    private List<CoreCustomer> withoutLockingScore(List<CoreCustomer> page) {
        Set<String> locked = riskResultRepository.findCifsWithLatestScore(
                page.stream().map(CoreCustomer::getCif).toList(), LOCKING_SCORE);
        return locked.isEmpty() ? page : page.stream().filter(c -> !locked.contains(c.getCif())).toList();
    }
}
