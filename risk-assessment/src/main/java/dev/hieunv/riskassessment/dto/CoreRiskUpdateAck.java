package dev.hieunv.riskassessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/** Phản hồi của Core ví. */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoreRiskUpdateAck {

    private String cif;

    /** true = Core đã từng nhận lệnh này rồi và bỏ qua lần này. */
    private boolean duplicate;

    /** Core có thực sự khóa CIF trong lần xử lý này không. */
    private boolean cifLocked;

    private String message;
}
