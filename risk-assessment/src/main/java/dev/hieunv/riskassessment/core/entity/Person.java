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
@Table(name = "person")
@Getter
@ToString
@NoArgsConstructor
public class Person {

    @Id
    @Column(name = "person_id", nullable = false)
    private Long personId;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "id_type", length = 1)
    private String idType;

    @Column(name = "id_number", length = 30)
    private String idNumber;

    @Column(name = "phone_number", length = 50)
    private String phoneNumber;

    @Column(name = "nationality_code", length = 10)
    private String nationalityCode;
}
