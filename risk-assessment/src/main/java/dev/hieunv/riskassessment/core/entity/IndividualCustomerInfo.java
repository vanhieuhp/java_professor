package dev.hieunv.riskassessment.core.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.hibernate.annotations.Immutable;

import java.time.LocalDate;

@Entity
@Immutable
@Table(name = "individual_customer_info")
@Getter
@ToString
@NoArgsConstructor
public class IndividualCustomerInfo {

    @Id
    @Column(name = "customer_id", nullable = false, length = 50)
    private String customerId;

    @Column(name = "family_name", nullable = false, length = 100)
    private String familyName;

    @Column(name = "middle_name", length = 100)
    private String middleName;

    @Column(name = "given_name", nullable = false, length = 100)
    private String givenName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "nationality", length = 2)
    private String nationality;

    @Column(name = "residence_country", length = 2)
    private String residenceCountry;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "job_title", length = 100)
    private String jobTitle;
}
