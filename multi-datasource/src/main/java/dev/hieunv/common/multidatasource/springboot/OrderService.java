package dev.hieunv.common.multidatasource.springboot;

import dev.hieunv.common.multidatasource.springboot.entity.Order;
import dev.hieunv.common.multidatasource.springboot.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository repository;

    @Transactional("secondaryTransactionManager")
    public OrderDTO create(OrderDTO dto) {
        Order order = new Order();
        order.setItem(dto.item());
        order.setQuantity(dto.quantity());

        Order saved = repository.save(order);
        return new OrderDTO(saved.getId(), saved.getItem(), saved.getQuantity());
    }

    public List<OrderDTO> getAll() {
        return repository.findAll().stream()
                .map(o -> new OrderDTO(o.getId(), o.getItem(), o.getQuantity()))
                .toList();
    }
}
