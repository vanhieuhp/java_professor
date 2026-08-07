package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.MatchType;
import dev.hieunv.riskassessment.dto.AddWatchlistEntryRequest;
import dev.hieunv.riskassessment.entity.WatchlistCategory;
import dev.hieunv.riskassessment.entity.WatchlistEntry;
import dev.hieunv.riskassessment.utils.Normalizer;
import dev.hieunv.riskassessment.repository.WatchlistCategoryRepository;
import dev.hieunv.riskassessment.repository.WatchlistEntryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Sửa đổi danh sách — mô phỏng màn hình quản trị.
 * <p>
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

        log.warn("Đã thêm bản ghi vào [{}]{}", category.getCode(),
                category.isBlacklist() ? " — DS ĐEN, TH1 sẽ được kích hoạt" : "");
        return entry.getId();
    }

    /**
     * Chỉ ghi đúng nhóm cột của {@link MatchType} tương ứng, và luôn ghi kèm cột chuẩn hóa.
     * Cả hai ràng buộc này DB cũng kiểm tra lại bằng {@code ck_entry_shape} và
     * {@code ck_entry_norm} — code sai thì insert bị từ chối chứ không lặng lẽ tạo ra bản
     * ghi không bao giờ match được.
     */
    private WatchlistEntry build(WatchlistCategory category, AddWatchlistEntryRequest r) {
        WatchlistEntry.UserBlackListBuilder builder = WatchlistEntry.builder()
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
