package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.utils.Normalizer;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Data
@AllArgsConstructor
public class CIFAttributeIndex {

    private MatchType matchType;
    private Map<String, WatchlistEntry> byValue;

    public static CIFAttributeIndex build(MatchType matchType, List<WatchlistEntry> entries) {
        Map<String, WatchlistEntry> byValue = new HashMap<>();
        for (WatchlistEntry e : entries) {
            String value = Normalizer.code(valueOf(matchType, e));
            if (value != null) {
                byValue.putIfAbsent(value, e);
            }
        }
        return new CIFAttributeIndex(matchType, byValue);
    }

    public WatchlistEntry byValue(String normalizedCode) {
        if (normalizedCode == null) {
            return null;
        }
        return byValue.get(normalizedCode);
    }

    public Optional<MatchDetail> match(CustomerSnapshot customer) {
        String value = Normalizer.code(customerValue(customer));
        if (value == null) {
            return Optional.empty();
        }
        WatchlistEntry hit = byValue.get(value);
        if (hit == null) {
            return Optional.empty();
        }
        return Optional.of(MatchDetail.builder()
                .entryId(hit.getId())
                .matchedFields(EnumSet.of(field()))
                .build());
    }

    private String customerValue(CustomerSnapshot c) {
        return switch (matchType) {
            case K2 -> c.getCountryCode();
            case K3 -> c.getOccupationCode();
            case K4 -> c.getPositionCode();
            case K1 -> throw new IllegalStateException("K1 dùng IdentityIndex, không phải AttributeIndex");
        };
    }

    private static String valueOf(MatchType matchType, WatchlistEntry e) {
        return switch (matchType) {
            case K2 -> e.getCountryCode();
            case K3 -> e.getOccupationCode();
            case K4 -> e.getPositionCode();
            case K1 -> throw new IllegalStateException("K1 dùng IdentityIndex, không phải AttributeIndex");
        };
    }

    private MatchField field() {
        return switch (matchType) {
            case K2 -> MatchField.COUNTRY;
            case K3 -> MatchField.OCCUPATION;
            case K4 -> MatchField.POSITION;
            case K1 -> throw new IllegalStateException("K1 dùng IdentityIndex, không phải AttributeIndex");
        };
    }
}
