package dev.hieunv.riskassessment.core.entity;

import dev.hieunv.riskassessment.constant.RoleEnum;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class CustomerPersonRoleId implements Serializable {

    private String customerId;

    private Long personId;

    private RoleEnum role;
}
