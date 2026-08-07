package dev.hieunv.riskassessment.dto.watchlist;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.matching.CIFAttributeIndex;
import dev.hieunv.riskassessment.matching.CIFIdentityIndex;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.MatchDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public final class WatchlistCategoryIndex {

    private WatchlistCategory category;
    private int entryCount;
    private Function<CustomerSnapshot, Optional<MatchDetail>> matcher;
    private CIFIdentityIndex cifIdentityIndex;
    private CIFAttributeIndex cifAttributeIndex;

    public Optional<MatchDetail> match(CustomerSnapshot customer) {
        return matcher.apply(customer);
    }

    public static WatchlistCategoryIndex toIndex(Watchlist watchlist) {
        WatchlistCategory category = watchlist.getCategory();
        List<WatchlistEntry> entries = watchlist.getEntries();

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

