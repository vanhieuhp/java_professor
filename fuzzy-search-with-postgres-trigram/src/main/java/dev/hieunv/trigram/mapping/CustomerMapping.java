package dev.hieunv.trigram.mapping;

import dev.hieunv.trigram.dto.ContactDetailsDto;
import dev.hieunv.trigram.dto.CustomerDto;
import dev.hieunv.trigram.dto.PagedResult;
import dev.hieunv.trigram.entity.Address;
import dev.hieunv.trigram.entity.ContactDetails;
import dev.hieunv.trigram.entity.CustomerEntity;
import org.springframework.data.domain.Page;

import java.util.List;

public class CustomerMapping {

    // Equivalent to Page<U>.asPagedResult(content: List<T>)
    public static <U, T> PagedResult<T> asPagedResult(Page<U> page, List<T> content) {
        return new PagedResult<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    // Equivalent to CustomerEntity.asCustomerDto()
    public static CustomerDto asCustomerDto(CustomerEntity entity) {
        return new CustomerDto(entity.getId(),
                entity.getContractNumber(),
                asString(entity.getAddress()),
                asContactDetailsDto(entity.getContactDetails()));
    }

    // Equivalent to Address.asString()
    public static String asString(Address address) {
        return address.getZipCode() + " " + address.getCity() + ", " +
                address.getStreet() + " " + address.getStreetNumber();
    }

    // Equivalent to ContactDetails.asContactDetailsDto()
    public static ContactDetailsDto asContactDetailsDto(ContactDetails contactDetails) {
        String fullName = contactDetails.getFirstName() + " " + contactDetails.getLastName();
        return new ContactDetailsDto(fullName, contactDetails.getEmail(), contactDetails.getPhone());
    }
}
