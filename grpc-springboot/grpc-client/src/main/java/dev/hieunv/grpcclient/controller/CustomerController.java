package dev.hieunv.grpcclient.controller;

import com.google.protobuf.InvalidProtocolBufferException;
import dev.hieunv.grpcclient.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/{customerId}")
    private ResponseEntity<Object> getCustomer(@PathVariable("customerId") int customerId) throws InvalidProtocolBufferException {
        return ResponseEntity.ok(customerService.getCustomer(customerId));
    }

    @GetMapping("/list")
    private ResponseEntity<Object> getListCustomer() throws InterruptedException {
        return ResponseEntity.ok(customerService.getListCustomer());
    }
}
