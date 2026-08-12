package dev.hieunv.riskassessment.constant;

import lombok.Getter;

@Getter
public enum CustomerType {

    INDIVIDUAL("I"),
    ORGANIZATION("O");

    private final String code;

    CustomerType(String code) {
        this.code = code;
    }
}
