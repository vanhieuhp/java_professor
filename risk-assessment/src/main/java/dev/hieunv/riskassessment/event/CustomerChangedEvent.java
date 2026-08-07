package dev.hieunv.riskassessment.event;

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
public class CustomerChangedEvent {

    private String eventId;
    private String cif;
    private ChangeType changeType;

    // ----- Trạng thái đầy đủ của khách hàng SAU thay đổi -----
    private String fullName;
    private LocalDate dob;
    private String phone;
    private String idNumber;
    private String oldIdNumber;
    private String countryCode;
    private String occupationCode;
    private String positionCode;
    private String customerType;
    private String status;

    private Instant occurredAt;
}
