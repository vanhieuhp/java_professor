package dev.hieunv.dataintensive.reliability.controller.maintainability;

import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Slf4j
@RestController
public class OrderController {

    @GetMapping("/orders/{id}")
    public ResponseEntity<String> getOrder(@PathVariable String id) {
        String traceId = UUID.randomUUID().toString();
        MDC.put("traceId", traceId);

        log.info("Fetching order {}", id);

        // Simulate downstream call
        String response = "Order-" + id;
        log.info("Response: {}", response);

        MDC.clear();
        return ResponseEntity.ok(response);
    }
}
