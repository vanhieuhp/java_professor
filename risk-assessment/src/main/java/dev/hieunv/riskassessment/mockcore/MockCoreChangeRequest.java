package dev.hieunv.riskassessment.mockcore;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;

/**
 * PHÍA CORE VÍ — thân request "đổi thông tin khách hàng".
 * <p>
 * Trường nào để trống thì giữ nguyên giá trị cũ ({@code COALESCE} trong câu UPDATE). Nhờ vậy
 * gọi được một lần đổi đúng số điện thoại mà không phải gửi lại toàn bộ hồ sơ.
 * <p>
 * Chú ý: đây là thân của <b>request đổi dữ liệu</b>, khác hẳn với
 * {@link dev.hieunv.riskassessment.event.CustomerChangedEvent} — sự kiện phát ra sau đó mang
 * <b>toàn bộ</b> trạng thái sau thay đổi, không phải phần vừa đổi. Lý do ở Javadoc của lớp kia.
 */
@Getter
@Setter
@ToString
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockCoreChangeRequest {

    private String fullName;
    private LocalDate dob;
    private String phone;
    private String idNumber;
    private String oldIdNumber;
    private String countryCode;
    private String occupationCode;
    private String positionCode;

    /** ACTIVE | APPROVED | LOCKED | CLOSED. */
    private String status;
}
