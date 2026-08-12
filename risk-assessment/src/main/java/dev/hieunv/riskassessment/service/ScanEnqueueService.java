package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.TriggerType;

import java.time.Instant;
import java.util.UUID;

public interface ScanEnqueueService {
    int enqueueAllActive(UUID batchId, TriggerType triggerType, int pageSize);

    int enqueueChangedYesterday(UUID batchId, TriggerType triggerType, int pageSize,
                                Instant from, Instant to);
}
