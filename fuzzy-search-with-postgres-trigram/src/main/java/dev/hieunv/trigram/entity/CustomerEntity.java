package dev.hieunv.trigram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "customer")
public class CustomerEntity {

    @Id
    @Column(name = "id", columnDefinition = "uuid", unique = true, nullable = false)
    private UUID id;

    @Column(name = "contract_number")
    private String contractNumber;

    @Embedded
    private Address address;

    @Embedded
    private ContactDetails contactDetails;

    public CustomerEntity(UUID id, String contractNumber, Address address, ContactDetails contactDetails) {
        this.id = id != null ? id : UUID.randomUUID();
        this.contractNumber = contractNumber;
        this.address = address;
        this.contactDetails = contactDetails;
    }
}
