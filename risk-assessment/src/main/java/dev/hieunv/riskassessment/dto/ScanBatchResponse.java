package dev.hieunv.riskassessment.dto;

import dev.hieunv.riskassessment.constant.BatchStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.UUID;

/** Tiến độ một lần quét — để batch không phải là hộp đen. */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScanBatchResponse {

    private UUID batchId;
    private TriggerType triggerType;
    private BatchStatus status;

    private Integer enqueuedCount;
    private Integer processedCount;
    private Integer matchedCount;

    /** Số bản ghi còn ở trạng thái Chờ xử lý — đọc thẳng từ hàng đợi, không phải bộ đếm. */
    private Long pendingCount;

    private Instant startedAt;
    private Instant finishedAt;
    private String note;
}
