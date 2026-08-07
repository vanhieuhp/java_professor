package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.dto.WatchlistCachePayload;
import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.matching.CIFAttributeIndex;
import dev.hieunv.riskassessment.matching.CompiledCategory;
import dev.hieunv.riskassessment.matching.CIFIdentityIndex;
import dev.hieunv.riskassessment.matching.IdentityMatcher;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class WatchlistServiceImpl implements WatchlistService {

    private final WatchlistCategoryRepository watchlistCategoryRepo;
    private final WatchlistEntryRepository watchlistEntryRepo;
    private final WatchlistCacheService watchlistCache;
    private final IdentityMatcher identityMatcher = new IdentityMatcher();

    private volatile WatchlistSnapshot current = new WatchlistSnapshot(null, List.of(), null);

    @PostConstruct
    public void loadOnStartup() {
        reload();
    }

    @Scheduled(fixedDelayString = "${pcrt.watchlist-cache.refresh-interval-ms:30000}")
    public void refreshIfChanged() {
        Instant latest = watchlistCategoryRepo.findLatestEntriesChangedAt();
        WatchlistSnapshot snapshot = current;
        if (snapshot.getBlacklist() != null && Objects.equals(latest, snapshot.getLoadedFrom())) {
            return;
        }
        log.info("cif evaluate category is changed -> reload the cache");
        reload();
    }

    @Override
    public synchronized void reload() {
        Instant mark = watchlistCategoryRepo.findLatestEntriesChangedAt();

        Optional<WatchlistCachePayload> cached = watchlistCache.find(mark);
        if (cached.isPresent()) {
            current = buildSnapshot(cached.get());
            return;
        }

        WatchlistCachePayload payload = readFromDatabase(mark);
        current = buildSnapshot(payload);
        watchlistCache.save(payload);
    }

    @Override
    public WatchlistSnapshot snapshot() {
        return current;
    }

    @Override
    public WatchlistSnapshot freshSnapshot() {
        refreshIfChanged();
        return current;
    }

    @Override
    public Optional<CompiledCategory> blacklist() {
        return Optional.ofNullable(current.getBlacklist());
    }

    private WatchlistCachePayload readFromDatabase(Instant mark) {
        WatchlistCachePayload payload = new WatchlistCachePayload();
        payload.setLoadedFrom(mark);
        payload.setSchemaVersion(WatchlistCachePayload.CURRENT_SCHEMA_VERSION);

        // build blacklist rules
        WatchlistCategory blacklistCategory = watchlistCategoryRepo.findBlacklistCategory().orElse(null);
        if (blacklistCategory != null) {
            List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(blacklistCategory.getId());
            WatchlistCachePayload.CachedCategory blacklist = new WatchlistCachePayload.CachedCategory(blacklistCategory, entries);
            payload.setBlacklist(blacklist);
        }

        // build cif evaluate lists
        List<WatchlistCachePayload.CachedCategory> cifWatchlist = new ArrayList<>();
        for (WatchlistCategory category : watchlistCategoryRepo.findCifWatchlist()) {
            cifWatchlist.add(buildCachedCategory(category));
        }
        payload.setCifEvaluateLists(cifWatchlist);

        return payload;
    }

    private WatchlistCachePayload.CachedCategory buildCachedCategory(WatchlistCategory category) {
        List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(category.getId());
        return new WatchlistCachePayload.CachedCategory(category, entries);
    }

    private WatchlistSnapshot buildSnapshot(WatchlistCachePayload payload) {
        WatchlistSnapshot snapshot = new WatchlistSnapshot();
        if (payload.getBlacklist() != null) {
            snapshot.setBlacklist(compile(payload.getBlacklist().getCategory(), payload.getBlacklist().getEntries(), identityMatcher));
        }

        if (!CollectionUtils.isEmpty(payload.getCifEvaluateLists())) {
            List<CompiledCategory> compiled = new ArrayList<>();
            for (WatchlistCachePayload.CachedCategory cached : payload.getCifEvaluateLists()) {
                compiled.add(compile(cached.getCategory(), cached.getEntries(), identityMatcher));
            }
            snapshot.setCifEvaluateLists(compiled);
        }

        return snapshot;
    }

    public CompiledCategory compile(WatchlistCategory category,
                                    List<WatchlistEntry> entries,
                                    IdentityMatcher identityMatcher) {
        if (category.getMatchType() == MatchType.K1) {
            CIFIdentityIndex index = CIFIdentityIndex.build(entries);
            return CompiledCategory.builder()
                    .category(category)
                    .cifIdentityIndex(index)
                    .
                    .build();
            return new CompiledCategory(category, entries.size(), c -> identityMatcher.match(c, index));
        }
        CIFAttributeIndex index = CIFAttributeIndex.build(category.getMatchType(), entries);
        return new CompiledCategory(category, entries.size(), index::match);
    }

}
