package dev.hieunv.riskassessment.dto.watchlist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WatchlistStatusResponse {

    private Instant loadedFrom;
    private int totalEntries;
    private List<WatchlistCategoryDto> categories;

}
