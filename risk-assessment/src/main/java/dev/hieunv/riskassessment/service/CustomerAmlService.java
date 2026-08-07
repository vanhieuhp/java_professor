package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import org.springframework.transaction.annotation.Transactional;

public interface CustomerAmlService {
    @Transactional
    EvaluateCustomerResponse evaluateAgainstBlacklist(CustomerEvaluateRequest request);
}
