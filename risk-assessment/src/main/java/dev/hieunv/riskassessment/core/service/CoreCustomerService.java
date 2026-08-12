package dev.hieunv.riskassessment.core.service;

import dev.hieunv.riskassessment.core.CoreCustomer;

import java.time.Instant;
import java.util.List;

public interface CoreCustomerService {
    List<CoreCustomer> nextScanTargetPage(String afterId, int limit);

    List<CoreCustomer> nextEnrolledBetweenPage(Instant from, Instant to, String afterId, int limit);
}
