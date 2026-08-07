package dev.hieunv.riskassessment.matching;

import dev.hieunv.riskassessment.entity.WatchlistEntry;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AllArgsConstructor
@Data
public class CIFIdentityIndex {

    private Map<String, List<WatchlistEntry>> byIdNumber;
    private Map<String, List<WatchlistEntry>> byFullName;
    private Map<String, List<WatchlistEntry>> byPhone;
    private Map<LocalDate, List<WatchlistEntry>> byDob;

    public static CIFIdentityIndex build(List<WatchlistEntry> entries) {
        Map<String, List<WatchlistEntry>> byIdNumber = new HashMap<>();
        Map<String, List<WatchlistEntry>> byFullName = new HashMap<>();
        Map<String, List<WatchlistEntry>> byPhone = new HashMap<>();
        Map<LocalDate, List<WatchlistEntry>> byDob = new HashMap<>();

        for (WatchlistEntry e : entries) {
            put(byIdNumber, e.getIdNumberNorm(), e);
            put(byFullName, e.getFullNameNorm(), e);
            put(byPhone, e.getPhoneNorm(), e);
            put(byDob, e.getDob(), e);
        }
        return new CIFIdentityIndex(byIdNumber, byFullName, byPhone, byDob);
    }

    private static <K> void put(Map<K, List<WatchlistEntry>> index, K key, WatchlistEntry entry) {
        if (key == null) {
            return;
        }
        index.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(entry);
    }

    public List<WatchlistEntry> byIdNumber(String idNumberNorm) {
        return lookup(byIdNumber, idNumberNorm);
    }

    public List<WatchlistEntry> byFullName(String fullNameNorm) {
        return lookup(byFullName, fullNameNorm);
    }

    public List<WatchlistEntry> byPhone(String phoneNorm) {
        return lookup(byPhone, phoneNorm);
    }

    public List<WatchlistEntry> byDob(LocalDate dob) {
        return lookup(byDob, dob);
    }

    private static <K> List<WatchlistEntry> lookup(Map<K, List<WatchlistEntry>> index, K key) {
        if (key == null) {
            return Collections.emptyList();
        }
        return index.getOrDefault(key, Collections.emptyList());
    }
}
