package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.watchlist.Watchlist;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCachePayload;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryDto;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryIndex;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistStatusResponse;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
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
    public WatchlistSnapshot getSnapshot() {
        return current;
    }

    @Override
    public WatchlistSnapshot freshSnapshot() {
        refreshIfChanged();
        return current;
    }

    @Override
    public Optional<WatchlistCategoryIndex> blacklist() {
        return Optional.ofNullable(current.getBlacklist());
    }

    @Override
    public WatchlistStatusResponse getWatchlistStatus() {
        WatchlistSnapshot snapshot = getSnapshot();

        List<WatchlistCategoryIndex> all = new ArrayList<>();
        if (snapshot.getBlacklist() != null) {
            all.add(snapshot.getBlacklist());
        }
        all.addAll(snapshot.getCifEvaluateLists());

        List<WatchlistCategoryDto> categories = all.stream()
                .map(c -> WatchlistCategoryDto.builder()
                        .priority(c.getCategory().getPriority())
                        .subOrder(c.getCategory().getSubOrder())
                        .code(c.getCategory().getCode())
                        .name(c.getCategory().getName())
                        .matchType(c.getCategory().getMatchType())
                        .riskLevel(c.getCategory().getRiskLevel())
                        .riskScore(c.getCategory().getRiskScore())
                        .entryCount(c.getEntryCount())
                        .blacklist(c.getCategory().isBlacklist())
                        .build())
                .toList();

        return WatchlistStatusResponse.builder()
                .loadedFrom(snapshot.getLoadedFrom())
                .totalEntries(categories.stream().mapToInt(WatchlistCategoryDto::getEntryCount).sum())
                .categories(categories)
                .build();
    }

    private WatchlistCachePayload readFromDatabase(Instant mark) {
        WatchlistCachePayload payload = new WatchlistCachePayload();
        payload.setLoadedFrom(mark);
        payload.setSchemaVersion(WatchlistCachePayload.CURRENT_SCHEMA_VERSION);

        // build blacklist rules
        WatchlistCategory blacklistCategory = watchlistCategoryRepo.findBlacklistCategory().orElse(null);
        if (blacklistCategory != null) {
            List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(blacklistCategory.getId());
            Watchlist blacklist = new Watchlist(blacklistCategory, entries);
            payload.setBlacklist(blacklist);
        }

        // build cif evaluate lists
        List<Watchlist> cifWatchlist = new ArrayList<>();
        for (WatchlistCategory category : watchlistCategoryRepo.findCifWatchlist()) {
            List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(category.getId());
            cifWatchlist.add(new Watchlist(category, entries));
        }
        payload.setCifEvaluateLists(cifWatchlist);

        return payload;
    }

    private WatchlistSnapshot buildSnapshot(WatchlistCachePayload payload) {
        WatchlistSnapshot snapshot = new WatchlistSnapshot();
        if (payload.getBlacklist() != null) {
            snapshot.setBlacklist(WatchlistCategoryIndex.toIndex(payload.getBlacklist()));
        }

        if (!CollectionUtils.isEmpty(payload.getCifEvaluateLists())) {
            List<WatchlistCategoryIndex> compiled = new ArrayList<>();
            for (Watchlist watchlist : payload.getCifEvaluateLists()) {
                compiled.add(WatchlistCategoryIndex.toIndex(watchlist));
            }
            snapshot.setCifEvaluateLists(compiled);
        }

        return snapshot;
    }
}
