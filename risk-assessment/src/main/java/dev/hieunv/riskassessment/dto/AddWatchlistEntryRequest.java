package dev.hieunv.riskassessment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * Thêm một bản ghi vào danh sách — mô phỏng thao tác của màn hình quản trị.
 * Thêm vào DS đen chính là "DS đen được điều chỉnh" ở T1, và sẽ kích hoạt TH1.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddWatchlistEntryRequest {

    @NotBlank
    private String categoryCode;

    // K1
    private String fullName;
    private LocalDate dob;
    private String phone;
    private String idNumber;

    // K2 / K3 / K4
    private String countryCode;
    private String occupationCode;
    private String positionCode;

    private String source;
    private String sourceRef;
}
