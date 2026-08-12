package dev.hieunv.riskassessment.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.time.Instant;


@Entity
@Immutable
@Table(name = "customer")
@Getter
@ToString
@NoArgsConstructor
public class Customer {

    @Id
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "customer_type", nullable = false, length = 1)
    private String customerType;

    @Column(name = "customer_status", nullable = false, length = 1)
    private String customerStatus;

    @Column(name = "kyc_status", length = 1)
    private String kycStatus;

    @Column(name = "official_name", length = 100)
    private String officialName;

    @Column(name = "english_name", length = 100)
    private String englishName;

    @Column(name = "phone_number", nullable = false, length = 50)
    private String phoneNumber;

    @Column(name = "enrollment_date")
    private Instant enrollmentDate;
}
