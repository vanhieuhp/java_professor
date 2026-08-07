package dev.hieunv.riskassessment.dto.watchlist;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Getter
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Setter
public class WatchlistSnapshot {

    private WatchlistCategoryIndex blacklist;

    private List<WatchlistCategoryIndex> cifEvaluateLists;

    private Instant loadedFrom;
}
