package dev.hieunv.reactive.service;

import dev.hieunv.reactive.dao.CustomerDao;
import dev.hieunv.reactive.dto.Customer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerServiceImpl implements CustomerService {

    private final CustomerDao customerDao;

    @Override
    public List<Customer> getAllCustomers() {
        long start = System.currentTimeMillis();
        List<Customer> customers = customerDao.getCustomers();
        long end = System.currentTimeMillis();
        log.info("Time taken to fetch customers: {} ms", end - start);
        return customers;
    }

    @Override
    public Flux<Customer> loadAllCustomers() {
        long start = System.currentTimeMillis();
        Flux<Customer> customers = customerDao.loadAllCustomers();
        long end = System.currentTimeMillis();
        log.info("Time taken to fetch customers: {} ms", end - start);
        return customers;
    }
}
