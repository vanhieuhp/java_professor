package dev.hieunv.riskassessment.entity;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/**
 * Một lần quét. Tồn tại để lần quét trở thành thứ quan sát được: đang ở bước nào,
 * đã nạp bao nhiêu khách hàng, xử lý tới đâu, tìm thấy bao nhiêu ca trùng.
 * <p>
 * Không có bảng này thì một batch chạy 5 triệu khách hàng là một hộp đen — không biết
 * nó còn sống hay đã chết, không biết chạy lại thì mất bao lâu.
 */
@Entity
@Table(name = "scan_batch")
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBatch {

    @Id
    @Column(name = "id")
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 4)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BatchStatus status;

    @Column(name = "enqueued_count", nullable = false)
    @Builder.Default
    private Integer enqueuedCount = 0;

    @Column(name = "processed_count", nullable = false)
    @Builder.Default
    private Integer processedCount = 0;

    @Column(name = "matched_count", nullable = false)
    @Builder.Default
    private Integer matchedCount = 0;

    @Column(name = "started_at", nullable = false, insertable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "note", length = 500)
    private String note;
}
