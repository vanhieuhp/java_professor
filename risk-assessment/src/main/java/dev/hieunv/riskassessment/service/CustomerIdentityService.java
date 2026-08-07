package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface CustomerIdentityService {
    CustomerIdentityServiceImpl.SyncResult syncDelta();

    @Transactional
    boolean upsertFromRequest(CustomerEvaluateRequest r);

    @Transactional
    boolean upsertFromEvent(CustomerChangedEvent event);

    @Transactional
    boolean retire(String cif, Instant occurredAt);
}
