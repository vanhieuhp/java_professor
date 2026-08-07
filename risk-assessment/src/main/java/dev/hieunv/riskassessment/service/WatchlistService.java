package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryIndex;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistStatusResponse;

import java.util.Optional;

public interface WatchlistService {
    void reload();

    WatchlistSnapshot getSnapshot();

    WatchlistSnapshot freshSnapshot();

    Optional<WatchlistCategoryIndex> blacklist();

    WatchlistStatusResponse getWatchlistStatus();
}
