package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.entity.WatchlistCategory;
import lombok.Getter;

import java.util.Optional;
import java.util.function.Function;

@Getter
public final class CompiledCategory {

    private WatchlistCategory category;
    private int entryCount;
    private Function<CustomerSnapshot, Optional<MatchDetail>> matcher;
    private CIFIdentityIndex cifIdentityIndex;
    private CIFAttributeIndex cifAttributeIndex;

    public CompiledCategory(WatchlistCategory category,
                             int entryCount,
                             Function<CustomerSnapshot, Optional<MatchDetail>> matcher) {
        this.category = category;
        this.entryCount = entryCount;
        this.matcher = matcher;
    }

    public Optional<MatchDetail> match(CustomerSnapshot customer) {
        return matcher.apply(customer);
    }
}
