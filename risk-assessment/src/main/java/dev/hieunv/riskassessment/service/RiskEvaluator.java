package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.RiskAssessment;

import java.util.Optional;

public interface RiskEvaluator {
    Optional<RiskAssessment> evaluateAgainstWatchlists(CustomerSnapshot customer,
                                                       WatchlistSnapshot lists);

    Optional<RiskAssessment> evaluateAgainstBlacklist(CustomerSnapshot customer,
                                                      WatchlistSnapshot lists);
}
