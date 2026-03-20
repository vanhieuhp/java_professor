package dev.hieunv.reactive.handler;

import dev.hieunv.reactive.dao.CustomerDao;
import dev.hieunv.reactive.dto.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerHandler {

    private final CustomerDao customerDao;

    public Mono<ServerResponse> loadCustomers(ServerRequest request) {
        Flux<Customer> customers = customerDao.loadAllCustomersForHandler();
        return ServerResponse.ok().body(customers, Customer.class);
    }

    public Mono<ServerResponse> loadCustomerStream(ServerRequest request) {
        Flux<Customer> customerMono = customerDao.loadAllCustomers();
        return ServerResponse.ok().body(customerMono, Customer.class);
    }
}
