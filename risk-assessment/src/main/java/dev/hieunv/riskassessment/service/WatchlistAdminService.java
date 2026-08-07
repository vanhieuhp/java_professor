package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.dto.AddWatchlistEntryRequest;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import dev.hieunv.riskassessment.utils.Normalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Sửa đổi danh sách — mô phỏng màn hình quản trị.
 * Mỗi lần sửa đều cập nhật {@code entries_changed_at} của danh sách. Một cột đó phục vụ ba
 * việc khác nhau, và đó là lý do nó tồn tại: nạp lại cache trong RAM, chọn nhánh T3A/T3B
 * ở bước B1 của TH3, và kích hoạt TH1 khi danh sách bị sửa là DS đen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WatchlistAdminService {

    private final WatchlistCategoryRepository categoryRepository;
    private final WatchlistEntryRepository entryRepository;

    @Transactional
    public Long addEntry(AddWatchlistEntryRequest request) {
        WatchlistCategory category = categoryRepository.findAll().stream()
                .filter(c -> c.getCode().equalsIgnoreCase(request.getCategoryCode()))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException(
                        "Không có danh sách nào mã " + request.getCategoryCode()));

        WatchlistEntry entry = build(category, request);
        entryRepository.save(entry);

        // Đánh dấu danh sách đã đổi. Với DS đen, BlacklistChangeDetector sẽ thấy và
        // kích hoạt TH1 quét toàn bộ khách hàng.
        categoryRepository.touchByCode(category.getCode());

        log.warn("Added entry to [{}]{}", category.getCode(), category.isBlacklist() ? " — BLACKLIST, TH1 will be triggered" : "");
        return entry.getId();
    }

    private WatchlistEntry build(WatchlistCategory category, AddWatchlistEntryRequest r) {
        WatchlistEntry.WatchlistEntryBuilder builder = WatchlistEntry.builder()
                .categoryId(category.getId())
                .matchType(category.getMatchType())
                .source(r.getSource() == null ? "MANUAL" : r.getSource())
                .sourceRef(r.getSourceRef())
                .active(true);

        switch (category.getMatchType()) {
            case K1 -> builder
                    .fullName(r.getFullName())
                    .fullNameNorm(Normalizer.name(r.getFullName()))
                    .dob(r.getDob())
                    .phone(r.getPhone())
                    .phoneNorm(Normalizer.phone(r.getPhone()))
                    .idNumber(r.getIdNumber())
                    .idNumberNorm(Normalizer.idNumber(r.getIdNumber()));
            case K2 -> builder.countryCode(Normalizer.code(r.getCountryCode()));
            case K3 -> builder.occupationCode(Normalizer.code(r.getOccupationCode()));
            case K4 -> builder.positionCode(Normalizer.code(r.getPositionCode()));
        }
        return builder.build();
    }
}
