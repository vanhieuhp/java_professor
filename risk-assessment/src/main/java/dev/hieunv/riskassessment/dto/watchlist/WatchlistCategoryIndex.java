package dev.hieunv.riskassessment.dto.watchlist;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.matching.CIFAttributeIndex;
import dev.hieunv.riskassessment.matching.CIFIdentityIndex;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public final class WatchlistCategoryIndex {

    private WatchlistCategory category;
    private int entryCount;
    private CIFIdentityIndex cifIdentityIndex;
    private CIFAttributeIndex cifAttributeIndex;

    public static WatchlistCategoryIndex toIndex(WatchlistCategory category, List<WatchlistEntry> entries) {
        if (category.getMatchType() == MatchType.K1) {
            CIFIdentityIndex index = CIFIdentityIndex.build(entries);
            return WatchlistCategoryIndex.builder()
                    .category(category)
                    .entryCount(entries.size())
                    .cifIdentityIndex(index)
                    .build();
        }
        CIFAttributeIndex index = CIFAttributeIndex.build(category.getMatchType(), entries);
        return WatchlistCategoryIndex.builder()
                .category(category)
                .entryCount(entries.size())
                .cifAttributeIndex(index)
                .build();
    }
}

