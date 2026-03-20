package dev.hieunv.reactive.service;

import dev.hieunv.reactive.dto.Customer;
import reactor.core.publisher.Flux;

import java.util.List;

public interface CustomerService {

    List<Customer> getAllCustomers();

    Flux<Customer> loadAllCustomers();
}
