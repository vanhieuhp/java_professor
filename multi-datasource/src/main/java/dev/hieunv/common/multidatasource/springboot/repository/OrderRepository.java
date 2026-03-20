package dev.hieunv.common.multidatasource.springboot.repository;

import dev.hieunv.common.multidatasource.springboot.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {

}
