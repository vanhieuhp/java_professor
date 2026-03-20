package dev.hieunv.dataintensive.reliability.controller.reliability;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.Random;

@RequiredArgsConstructor
@RestController
@RequestMapping("/mock-bank")
public class MockBankController {

    private final Random random = new Random();

    @PostMapping("/charge")
    public ResponseEntity<String> charge(@RequestBody Map<String, Object> payload) throws InterruptedException {
        // Simulate random delay (0–5s)
        int delay = random.nextInt(5000);
        Thread.sleep(delay);

        // 25% chance to fail
        if (random.nextDouble() < 0.25) {
            throw new RuntimeException("Random bank failure");
        }

        return ResponseEntity.ok("CHARGED after " + delay + "ms");
    }
}
