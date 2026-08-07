package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.matching.CompiledCategory;

import java.util.Optional;

public interface WatchlistService {
    void reload();

    WatchlistSnapshot snapshot();

    WatchlistSnapshot freshSnapshot();

    Optional<CompiledCategory> blacklist();
}
