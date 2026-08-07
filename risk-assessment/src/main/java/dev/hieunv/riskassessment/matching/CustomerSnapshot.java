package dev.hieunv.riskassessment.matching;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSnapshot {

    private String cif;

    // ----- K1: định danh -----
    private String fullNameNorm;
    private LocalDate dob;
    private String phoneNorm;
    private String idNumberNorm;

    /** Số GTTT cũ. Có được dùng để so khớp hay không: Q3 — chưa chốt với BA. */
    private String oldIdNumberNorm;

    // ----- K2 / K3 / K4 -----
    private String countryCode;
    private String occupationCode;
    private String positionCode;
}
