package dev.hieunv.riskassessment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertCustomerRequest {
    private String cif;
    private boolean scanTarget;
    private String fullName;
    private LocalDate dob;
    private String phone;
    private String idNumber;
    private String oldIdNumber;
    private Instant occurredAt;
}
