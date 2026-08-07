package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryDto;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistCategoryIndex;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistStatusResponse;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import dev.hieunv.riskassessment.service.WatchlistService;
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
        Instant lastTime = watchlistCategoryRepo.findLatestEntriesChangedAt();
        current = buildSnapshot(lastTime);
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

    private WatchlistSnapshot buildSnapshot(Instant lastTime) {
        WatchlistSnapshot snapshot = new WatchlistSnapshot();
        snapshot.setLoadedFrom(lastTime);
        snapshot.setCifEvaluateLists(new ArrayList<>());

        // build blacklist rules
        WatchlistCategory blacklistCategory = watchlistCategoryRepo.findBlacklistCategory().orElse(null);
        if (blacklistCategory != null) {
            List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(blacklistCategory.getId());
            snapshot.setBlacklist(WatchlistCategoryIndex.toIndex(blacklistCategory, entries));
        }

        // build cif evaluate lists
        for (WatchlistCategory category : watchlistCategoryRepo.findCifWatchlist()) {
            List<WatchlistEntry> entries = watchlistEntryRepo.findByCategoryIdAndActiveTrue(category.getId());
            if (CollectionUtils.isEmpty(entries)) {
                continue;
            }
            WatchlistCategoryIndex index = WatchlistCategoryIndex.toIndex(category, entries);
            snapshot.getCifEvaluateLists().add(index);
        }

        return snapshot;
    }

}
