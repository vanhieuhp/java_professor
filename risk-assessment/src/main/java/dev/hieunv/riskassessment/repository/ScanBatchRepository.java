package dev.hieunv.riskassessment.repository;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.entity.ScanBatch;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ScanBatchRepository extends JpaRepository<ScanBatch, UUID> {

    /** Q9 — các lần quét còn dở, để tiếp tục sau khi job hoặc cả service chết giữa chừng. */
    List<ScanBatch> findByStatusInOrderByStartedAtAsc(List<BatchStatus> statuses);

    List<ScanBatch> findAllByOrderByStartedAtDesc(Limit limit);
}
