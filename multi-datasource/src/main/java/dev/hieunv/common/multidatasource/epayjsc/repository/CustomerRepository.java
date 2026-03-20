package dev.hieunv.common.multidatasource.epayjsc.repository;

import dev.hieunv.common.multidatasource.epayjsc.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}
