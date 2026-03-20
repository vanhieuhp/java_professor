package dev.hieunv.common.multidatasource.controller;

import dev.hieunv.common.multidatasource.epayjsc.CustomerDTO;
import dev.hieunv.common.multidatasource.epayjsc.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService service;

    @PostMapping
    public CustomerDTO create(@RequestBody CustomerDTO dto) {
        return service.create(dto);
    }

    @GetMapping
    public List<CustomerDTO> getAll() {
        return service.getAll();
    }
}