package dev.hieunv.dataintensive.reliability.controller.scalability;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Random;

@RestController
@RequestMapping("/latency")
public class LatencyController {

    private final Random random = new Random();

    @GetMapping
    public ResponseEntity<String> simulateLatency() throws InterruptedException {
        // Random latency: 50ms to 2000ms
        int delay = 50 + random.nextInt(1950);
        Thread.sleep(delay);

        return ResponseEntity.ok("Response after " + delay + " ms");
    }
}