package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistCachePayload {

    public static final int CURRENT_SCHEMA_VERSION = 2;

    private int schemaVersion;

    private Instant loadedFrom;

    private CachedCategory blacklist;

    private List<CachedCategory> cifEvaluateLists;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CachedCategory {

        private WatchlistCategory category;

        private List<WatchlistEntry> entries;
    }
}
