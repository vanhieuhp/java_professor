package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CustomerScanEventRepository extends JpaRepository<CustomerScanEvent, Long> {

    /**
     * Trạng thái so bằng HẰNG ENUM, không bằng chuỗi.
     * <p>
     * Bản cũ viết {@code c.status = 'CXL'}. Khi mã trạng thái đổi sang PENDING/PROCESSED, câu
     * này không khớp dòng nào nữa — và vì "không khớp dòng nào" trông y hệt "đã xử lý xong",
     * mọi lần quét đều báo COMPLETED với processed = 0. Hằng enum thì Hibernate phân giải lúc
     * khởi động: đổi tên hằng là app không lên, chứ không phải im lặng bỏ sót cả một lần quét.
     */
    @Query(value = """
            SELECT c FROM CustomerScanEvent c
            WHERE c.status = dev.hieunv.riskassessment.constant.ScanStatus.PENDING
              AND c.scanBatchId = :batchId
            ORDER BY c.id
            LIMIT :limit
            """)
    List<CustomerScanEvent> findPendingBatch(@Param("batchId") UUID batchId, @Param("limit") int limit);

    long countByScanBatchIdAndStatus(UUID scanBatchId, ScanStatus status);

}
