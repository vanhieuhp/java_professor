package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.entity.WatchlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface WatchlistEntryRepository extends JpaRepository<WatchlistEntry, Long> {

    List<WatchlistEntry> findByCategoryIdAndActiveTrue(Long categoryId);

    long countByCategoryIdAndActiveTrue(Long categoryId);

    /**
     * DELTA của danh sách — đầu vào của quét ngược.
     * <p>
     * Đây là nửa thứ hai của vấn đề, và nó độc lập với chuyện đổi chiều: TH1 hiện tại thêm
     * 1 bản ghi vào DS đen nhưng vẫn đem cả 100 bản ghi ra so khớp lại, dù 99 bản ghi kia
     * đã được so khớp ở lần quét trước và không đổi gì. Quét đúng delta của danh sách tiết
     * kiệm được ngay cả khi vẫn giữ chiều xuôi.
     */
    List<WatchlistEntry> findByCategoryIdAndActiveTrueAndUpdatedAtAfterOrderByIdAsc(
            Long categoryId, Instant since);
}
