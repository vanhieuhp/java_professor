package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.event.CustomerChangedEvent;
import dev.hieunv.riskassessment.service.impl.CustomerIdentityServiceImpl;

import java.time.Instant;

public interface CustomerIdentityService {
    CustomerIdentityServiceImpl.SyncResult fullSync();

    CustomerIdentityServiceImpl.SyncResult syncDelta();

    boolean upsertFromRequest(CustomerEvaluateRequest r);

    boolean upsertFromEvent(CustomerChangedEvent event);

    boolean retire(String cif, Instant occurredAt);
}
