package dev.hieunv.riskassessment.core;

import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@ToString
public class CoreCustomer {

    private final String cif;
    private final String customerType;
    private final String status;
    private final String kycStatus;
    private final String fullName;
    private final LocalDate dob;
    private final String phone;
    private final String idNumber;
    private final String countryCode;
    private final String occupationCode;
    private final String positionCode;
    private final Instant coreUpdatedAt;

    public CoreCustomer(String customerId,
                        String customerType,
                        String customerStatus,
                        String kycStatus,
                        String officialName,
                        String englishName,
                        String phoneNumber,
                        Instant enrollmentDate,
                        String familyName,
                        String middleName,
                        String givenName,
                        LocalDate dateOfBirth,
                        String nationality,
                        String occupation,
                        String jobTitle,
                        String personIdNumber) {
        this.cif = customerId;
        this.customerType = customerType;
        this.status = customerStatus;
        this.kycStatus = kycStatus;
        this.fullName = composeName(familyName, middleName, givenName, officialName, englishName);
        this.dob = dateOfBirth;
        this.phone = blankToNull(phoneNumber);
        this.idNumber = blankToNull(personIdNumber);
        this.countryCode = blankToNull(nationality);
        this.occupationCode = blankToNull(occupation);
        this.positionCode = blankToNull(jobTitle);
        this.coreUpdatedAt = enrollmentDate == null ? Instant.EPOCH : enrollmentDate;
    }


    public String getOldIdNumber() {
        return null;
    }

    private static String composeName(String familyName, String middleName, String givenName,
                                      String officialName, String englishName) {
        StringBuilder sb = new StringBuilder();
        appendPart(sb, familyName);
        appendPart(sb, middleName);
        appendPart(sb, givenName);
        if (!sb.isEmpty()) {
            return sb.toString();
        }
        String fallback = blankToNull(officialName);
        return fallback != null ? fallback : blankToNull(englishName);
    }

    private static void appendPart(StringBuilder sb, String part) {
        String value = blankToNull(part);
        if (value == null) {
            return;
        }
        if (!sb.isEmpty()) {
            sb.append(' ');
        }
        sb.append(value);
    }

    private static String blankToNull(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
