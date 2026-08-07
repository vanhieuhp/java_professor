package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.entity.WatchlistEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Component
public final class IdentityMatcher {

    private final boolean matchOldIdNumber;

    public IdentityMatcher() {
        this(false);
    }

    public IdentityMatcher(boolean matchOldIdNumber) {
        this.matchOldIdNumber = matchOldIdNumber;
    }

    public Optional<MatchDetail> match(CustomerSnapshot customer, CIFIdentityIndex index) {
        Optional<MatchDetail> byId = matchByIdNumber(customer.getIdNumberNorm(), index);
        if (byId.isPresent()) {
            return byId;
        }
        if (matchOldIdNumber) {
            Optional<MatchDetail> byOldId = matchByIdNumber(customer.getOldIdNumberNorm(), index);
            if (byOldId.isPresent()) {
                return byOldId;
            }
        }

        Map<Long, EnumSet<MatchField>> evidence = new HashMap<>();
        collect(evidence, index.byFullName(customer.getFullNameNorm()), MatchField.FULL_NAME);
        collect(evidence, index.byDob(customer.getDob()), MatchField.DOB);
        collect(evidence, index.byPhone(customer.getPhoneNorm()), MatchField.PHONE);

        return evidence.entrySet().stream()
                .filter(e -> e.getValue().size() >= 2)
                .min(Comparator.comparingLong(Map.Entry::getKey))
                .map(e -> MatchDetail.builder()
                        .entryId(e.getKey())
                        .matchedFields(e.getValue())
                        .build());
    }

    private Optional<MatchDetail> matchByIdNumber(String idNumberNorm, CIFIdentityIndex index) {
        return index.byIdNumber(idNumberNorm).stream()
                .min(Comparator.comparingLong(WatchlistEntry::getId))
                .map(e -> MatchDetail.builder()
                        .entryId(e.getId())
                        .matchedFields(EnumSet.of(MatchField.ID_NUMBER))
                        .build());
    }

    private static void collect(Map<Long, EnumSet<MatchField>> evidence,
                                List<WatchlistEntry> candidates,
                                MatchField field) {
        for (WatchlistEntry e : candidates) {
            evidence.computeIfAbsent(e.getId(), k -> EnumSet.noneOf(MatchField.class)).add(field);
        }
    }
}
