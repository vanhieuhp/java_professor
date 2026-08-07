package dev.hieunv.riskassessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Tình trạng hàng đợi gửi Core. */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreDispatchStats {

    private int sentThisRun;
    private int failedThisRun;
    private int skippedThisRun;

    private long pending;
    private long failed;
    private long sent;

    /** Đã hết lượt tự gửi — cần người can thiệp. */
    private long exhausted;

    private String circuitState;
    private int consecutiveFailures;
}
