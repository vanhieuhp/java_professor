package dev.hieunv.riskassessment.dto.watchlist;

import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Watchlist {

    private WatchlistCategory category;

    private List<WatchlistEntry> entries;
}
