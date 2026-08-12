package dev.hieunv.riskassessment.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum RoleEnum {
    LEGAL_REP("Legal Representative"),
    ACCOUNTANT("Accountant"),
    BENEFICIAL_OWNER("Beneficial Owner"),
    INITIAL_USER("Initial User");

    private final String desc;

    public static RoleEnum getByCode(String code) {
        for (RoleEnum role : values()) {
            if (role.name().equals(code)) {
                return role;
            }
        }
        return null;
    }
}
