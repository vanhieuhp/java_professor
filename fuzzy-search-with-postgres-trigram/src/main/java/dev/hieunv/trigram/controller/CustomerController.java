package dev.hieunv.trigram.controller;

import dev.hieunv.trigram.dto.CustomerDto;
import dev.hieunv.trigram.dto.PagedResult;
import dev.hieunv.trigram.service.CustomerSearchService;
import org.springframework.data.domain.Pageable;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/customers")
@Validated
public class CustomerController {

    private final CustomerSearchService customerSearchService;

    public CustomerController(CustomerSearchService customerSearchService) {
        this.customerSearchService = customerSearchService;
    }

    @GetMapping
    public PagedResult<CustomerDto> searchCustomers(
            @RequestParam("q") String search,
            Pageable pageable
    ) {
        return customerSearchService.searchCustomers(search, pageable);
    }

    @GetMapping("/specification")
    public PagedResult<CustomerDto> searchCustomersBySpecification(
            @RequestParam("q") String search,
            Pageable pageable
    ) {
        return customerSearchService.searchCustomersBySpecification(search, pageable);
    }

    @GetMapping("/native")
    public PagedResult<CustomerDto> searchCustomersByNativeQuery(
            @RequestParam("q") String search,
            Pageable pageable
    ) {
        return customerSearchService.searchCustomersByNativeQuery(search, pageable);
    }
}
