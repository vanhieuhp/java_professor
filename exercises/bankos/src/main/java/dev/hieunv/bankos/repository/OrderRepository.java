package dev.hieunv.bankos.repository;

import dev.hieunv.bankos.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findBySagaId(String sagaId);
}
