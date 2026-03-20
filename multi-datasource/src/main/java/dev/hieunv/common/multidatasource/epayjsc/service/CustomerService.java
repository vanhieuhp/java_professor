package dev.hieunv.common.multidatasource.epayjsc.service;

import dev.hieunv.common.multidatasource.epayjsc.CustomerDTO;
import dev.hieunv.common.multidatasource.epayjsc.entity.Customer;
import dev.hieunv.common.multidatasource.epayjsc.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository repository;

    @Transactional("primaryTransactionManager")
    public CustomerDTO create(CustomerDTO dto) {
        Customer customer = new Customer();
        customer.setName(dto.name());
        customer.setEmail(dto.email());

        Customer saved = repository.save(customer);
        return new CustomerDTO(saved.getId(), saved.getName(), saved.getEmail());
    }

    public List<CustomerDTO> getAll() {
        return repository.findAll().stream()
                .map(c -> new CustomerDTO(c.getId(), c.getName(), c.getEmail()))
                .toList();
    }
}
