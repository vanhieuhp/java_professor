package dev.hieunv.common.multidatasource.controller;

import dev.hieunv.common.multidatasource.springboot.OrderDTO;
import dev.hieunv.common.multidatasource.springboot.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService service;

    @PostMapping
    public OrderDTO create(@RequestBody OrderDTO dto) {
        return service.create(dto);
    }

    @GetMapping
    public List<OrderDTO> getAll() {
        return service.getAll();
    }
}
