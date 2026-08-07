package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerScanEventRepository extends JpaRepository<CustomerScanEvent, Long> {

    @Query(value = """
            SELECT c FROM CustomerScanEvent c
            WHERE c.status = 'CXL' AND c.scanBatchId = :batchId
            ORDER BY c.id
            LIMIT :limit
            """)
    List<CustomerScanEvent> findPendingBatch(@Param("batchId") UUID batchId, @Param("limit") int limit);

    /**
     * Bản dành cho nhiều instance PCRT chạy song song (Phase 7): mỗi instance khóa
     * phần việc của mình, các instance khác bỏ qua dòng đã bị khóa thay vì chờ.
     * Phải gọi trong một transaction đang mở.
     */
    @Query(value = """
            SELECT * FROM customer_scan_queue
            WHERE status = 'CXL' AND scan_batch_id = :batchId
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<CustomerScanEvent> claimPendingBatch(@Param("batchId") UUID batchId, @Param("limit") int limit);

    /**
     * B3 — đánh dấu đã xử lý. Điều kiện {@code status = 'CXL'} khiến câu này idempotent:
     * chạy lại lần hai trả về 0 dòng thay vì ghi đè {@code processed_at}.
     */
    @Modifying
    @Query(value = """
            UPDATE customer_scan_queue
            SET status = 'DA_XU_LY', processed_at = now()
            WHERE id = :id AND status = 'CXL'
            """, nativeQuery = true)
    int markProcessed(@Param("id") Long id);

    /**
     * B5 — còn KH chưa được đánh giá không?
     */
    long countByScanBatchIdAndStatus(UUID scanBatchId, ScanStatus status);

    /**
     * Resume sau khi job chết giữa chừng (Q9): tìm các lần quét còn dở.
     */
    @Query(value = """
            SELECT DISTINCT scan_batch_id FROM customer_scan_queue
            WHERE status = 'CXL'
            """, nativeQuery = true)
    List<UUID> findUnfinishedBatchIds();

    boolean existsByScanBatchIdAndCif(UUID scanBatchId, String cif);
}
