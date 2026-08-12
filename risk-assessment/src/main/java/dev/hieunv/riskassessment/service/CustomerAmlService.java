package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;

public interface CustomerAmlService {
    EvaluateCustomerResponse evaluateFromCoreEvent(CustomerEvaluateRequest request);

    EvaluateCustomerResponse evaluateAgainstBlacklist(CustomerEvaluateRequest request);

    EvaluateCustomerResponse evaluateAgainstWatchlists(CustomerEvaluateRequest request);
}
