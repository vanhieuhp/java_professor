package dev.hieunv.riskassessment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEvaluateRequest {

    @NotBlank(message = "Số CIF là bắt buộc")
    private String cif;

    @NotBlank(message = "Số GTTT là bắt buộc")
    private String idNumber;

    @NotBlank(message = "Họ tên là bắt buộc")
    private String fullName;

    @NotNull(message = "Ngày sinh là bắt buộc")
    @Past(message = "Ngày sinh phải ở quá khứ")
    private LocalDate dob;

    @NotBlank(message = "Số điện thoại là bắt buộc")
    private String phone;

    // ----- Không bắt buộc -----

    /**
     * Số GTTT cũ (CMND 9 số trước khi đổi CCCD) — Q3: có dùng để so khớp hay không, chưa chốt.
     */
    private String oldIdNumber;

    /**
     * Mã quốc gia trong địa chỉ KH. Tiêu chí K2.
     */
    private String countryCode;

    /**
     * Mã nghề nghiệp. Tiêu chí K3.
     */
    private String occupationCode;

    /**
     * Mã chức vụ. Tiêu chí K4.
     */
    private String positionCode;

    private String status;

    private Instant updateTime;
}
