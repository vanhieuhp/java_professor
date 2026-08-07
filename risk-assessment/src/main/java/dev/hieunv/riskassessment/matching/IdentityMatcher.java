package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.utils.Normalizer;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class IdentityMatcher {

    public Optional<MatchDetail> matchCifIdentity(CustomerSnapshot customer, CIFIdentityIndex index) {
        Optional<MatchDetail> byId = matchByIdNumber(customer.getIdNumberNorm(), index);
        if (byId.isPresent()) {
            return byId;
        }
//        if (matchOldIdNumber) {
//            Optional<MatchDetail> byOldId = matchByIdNumber(customer.getOldIdNumberNorm(), index);
//            if (byOldId.isPresent()) {
//                return byOldId;
//            }
//        }

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

    /**
     * Tiêu chí K2 / K3 / K4 — so khớp MỘT mã danh mục.
     * nghiệp vụ chứ không phải xử lý null cho có: coi "thiếu" là "trùng" sẽ gắn cờ toàn bộ
     * khách hàng chưa cập nhật hồ sơ.
     */
    public Optional<MatchDetail> matchCifAttribute(CustomerSnapshot customer, CIFAttributeIndex index) {
        MatchType matchType = index.getMatchType();

        String value = Normalizer.code(customerValue(customer, matchType));
        if (value == null) {
            return Optional.empty();
        }

        WatchlistEntry hit = index.byValue(value);
        if (hit == null) {
            return Optional.empty();
        }

        return Optional.of(MatchDetail.builder()
                .entryId(hit.getId())
                .matchedFields(EnumSet.of(fieldOf(matchType)))
                .build());
    }



    private static String customerValue(CustomerSnapshot customer, MatchType matchType) {
        return switch (matchType) {
            case K2 -> customer.getCountryCode();
            case K3 -> customer.getOccupationCode();
            case K4 -> customer.getPositionCode();
            case K1 -> throw new IllegalStateException(
                    "K1 dùng CIFIdentityIndex, không phải CIFAttributeIndex");
        };
    }

    private static MatchField fieldOf(MatchType matchType) {
        return switch (matchType) {
            case K2 -> MatchField.COUNTRY;
            case K3 -> MatchField.OCCUPATION;
            case K4 -> MatchField.POSITION;
            case K1 -> throw new IllegalStateException(
                    "K1 dùng CIFIdentityIndex, không phải CIFAttributeIndex");
        };
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
