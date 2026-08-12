package dev.hieunv.riskassessment.core.entity;

import dev.hieunv.riskassessment.constant.RoleEnum;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.math.BigDecimal;

@Entity
@Immutable
@Table(name = "customer_person_role")
@IdClass(CustomerPersonRoleId.class)
@Getter
@ToString
@NoArgsConstructor
public class CustomerPersonRole {

    @Id
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Id
    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 30)
    private RoleEnum role;

    @Column(name = "upd_seq")
    private Integer updSeq;

    @Column(name = "last_audit_id")
    private Long lastAuditId;

    @Column(name = "is_authorized_person")
    private Boolean authorizedPerson;

    @Column(name = "ownership_percentage", precision = 5, scale = 2)
    private BigDecimal ownershipPercentage;
}
