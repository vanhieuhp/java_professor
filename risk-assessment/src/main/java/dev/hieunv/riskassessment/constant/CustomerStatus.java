package dev.hieunv.riskassessment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Getter
@RequiredArgsConstructor
public enum CustomerStatus {
    CREATED("C", "Created"),
    APPROVED("P", "Approved"),
    ACTIVE("A", "Active"),
    LOCKED("L", "Locked"),
    CLOSED("X", "Closed");

    private final String code;
    private final String desc;

    public static CustomerStatus getByCode(String customerStatus) {
        for (CustomerStatus status : values()) {
            if (status.getCode().equals(customerStatus)) {
                return status;
            }
        }
        return null;
    }

    public static List<String> listActiveCodes() {
        return List.of(APPROVED.getCode(), ACTIVE.getCode());
    }
}
