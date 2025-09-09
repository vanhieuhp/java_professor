package dev.hieunv.trigram.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Embeddable
public class Address {

    @Column(name = "address_zip_code")
    private String zipCode;

    @Column(name = "address_city")
    private String city;

    @Column(name = "address_street")
    private String street;

    @Column(name = "address_street_number")
    private String streetNumber;
}
