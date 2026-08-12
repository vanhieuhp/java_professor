package dev.hieunv.riskassessment.core.repository;

import dev.hieunv.riskassessment.core.CoreCustomer;
import dev.hieunv.riskassessment.core.entity.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

public interface CoreCustomerRepository extends Repository<Customer, String> {


    @Query("""
            SELECT new dev.hieunv.riskassessment.core.CoreCustomer(
                                   c.customerId, c.customerType, c.customerStatus, c.kycStatus,
                                   c.officialName, c.englishName, c.phoneNumber, c.enrollmentDate,
                                   i.familyName, i.middleName, i.givenName, i.dateOfBirth,
                                   i.nationality, i.occupation, i.jobTitle,
                                   p.idNumber)
                        FROM Customer c
                        LEFT JOIN IndividualCustomerInfo i ON i.customerId = c.customerId
                        LEFT JOIN CustomerPersonRole r ON r.customerId = c.customerId
                        LEFT JOIN Person p ON p.personId = r.personId
                        WHERE c.customerType = :customerType
                          AND c.customerStatus IN :statuses
                          AND c.customerId > :afterId
                        ORDER BY c.customerId
            """)
    List<CoreCustomer> findScanTargetsAfter(@Param("customerType") String customerType,
                                            @Param("statuses") List<String> statuses,
                                            @Param("afterId") String afterId,
                                            Pageable page);

    @Query("""
            SELECT new dev.hieunv.riskassessment.core.CoreCustomer(
                                   c.customerId, c.customerType, c.customerStatus, c.kycStatus,
                                   c.officialName, c.englishName, c.phoneNumber, c.enrollmentDate,
                                   i.familyName, i.middleName, i.givenName, i.dateOfBirth,
                                   i.nationality, i.occupation, i.jobTitle,
                                   p.idNumber)
                        FROM Customer c
                        LEFT JOIN IndividualCustomerInfo i ON i.customerId = c.customerId
                        LEFT JOIN CustomerPersonRole r ON r.customerId = c.customerId
                        LEFT JOIN Person p ON p.personId = r.personId
                        WHERE c.customerStatus IN :statuses
                          and c.customerType = :customerType
                          AND c.enrollmentDate >= :from
                          AND c.enrollmentDate < :to
                          AND c.customerId > :afterId
                        ORDER BY c.customerId
            """)
    List<CoreCustomer> findEnrolledBetweenAfter(
            @Param("customerType") String customerType,
            @Param("statuses") List<String> statuses,
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("afterId") String afterId,
            Pageable page);

    @Query("""
            SELECT new dev.hieunv.riskassessment.core.CoreCustomer(
                                   c.customerId, c.customerType, c.customerStatus, c.kycStatus,
                                   c.officialName, c.englishName, c.phoneNumber, c.enrollmentDate,
                                   i.familyName, i.middleName, i.givenName, i.dateOfBirth,
                                   i.nationality, i.occupation, i.jobTitle,
                                   p.idNumber)
                        FROM Customer c
                        LEFT JOIN IndividualCustomerInfo i ON i.customerId = c.customerId
                        LEFT JOIN CustomerPersonRole r ON r.customerId = c.customerId
                        LEFT JOIN Person p ON p.personId = r.personId
            WHERE c.customerId > :afterId
                and c.customerType = :customerType
            ORDER BY c.customerId
            """)
    List<CoreCustomer> findAllAfter(@Param("afterId") String afterId,
                                    @Param("customerType") String customerType,
                                    Pageable page);

    @Query("""
            SELECT new dev.hieunv.riskassessment.core.CoreCustomer(
                                   c.customerId, c.customerType, c.customerStatus, c.kycStatus,
                                   c.officialName, c.englishName, c.phoneNumber, c.enrollmentDate,
                                   i.familyName, i.middleName, i.givenName, i.dateOfBirth,
                                   i.nationality, i.occupation, i.jobTitle,
                                   p.idNumber)
                        FROM Customer c
                        LEFT JOIN IndividualCustomerInfo i ON i.customerId = c.customerId
                        LEFT JOIN CustomerPersonRole r ON r.customerId = c.customerId
                        LEFT JOIN Person p ON p.personId = r.personId
            WHERE c.enrollmentDate > :since
              AND c.customerId > :afterId
            ORDER BY c.customerId
            """)
    List<CoreCustomer> findEnrolledAfter(@Param("since") Instant since,
                                         @Param("afterId") String afterId,
                                         @Param("customerType") String customerType,
                                         Pageable page);

    @Query("""
            SELECT new dev.hieunv.riskassessment.core.CoreCustomer(
                                   c.customerId, c.customerType, c.customerStatus, c.kycStatus,
                                   c.officialName, c.englishName, c.phoneNumber, c.enrollmentDate,
                                   i.familyName, i.middleName, i.givenName, i.dateOfBirth,
                                   i.nationality, i.occupation, i.jobTitle,
                                   p.idNumber)
                        FROM Customer c
                        LEFT JOIN IndividualCustomerInfo i ON i.customerId = c.customerId
                        LEFT JOIN CustomerPersonRole r ON r.customerId = c.customerId
                        LEFT JOIN Person p ON p.personId = r.personId
            WHERE c.customerId IN :cifs
            ORDER BY c.customerId
            """)
    List<CoreCustomer> findByCifIn(@Param("cifs") List<String> cifs,
                                   @Param("customerType") String customerType);

    @Query("""
            SELECT count(c) FROM Customer c
            WHERE c.customerStatus IN :statuses
            """)
    long countScanTargets(@Param("customerType") String customerType,
                          @Param("statuses") List<String> statuses);

    @Query(value = "SELECT now()", nativeQuery = true)
    Timestamp coreNow();
}
