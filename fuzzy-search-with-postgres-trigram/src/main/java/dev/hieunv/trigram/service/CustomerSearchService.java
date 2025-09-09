package dev.hieunv.trigram.service;

import dev.hieunv.trigram.dto.CustomerDto;
import dev.hieunv.trigram.dto.PagedResult;
import org.springframework.data.domain.Pageable;

public interface CustomerSearchService {

    PagedResult<CustomerDto> searchCustomers(String search, Pageable pageable);

    PagedResult<CustomerDto> searchCustomersByNativeQuery(String search, Pageable pageable);

    PagedResult<CustomerDto> searchCustomersBySpecification(String search, Pageable pageable);
}
