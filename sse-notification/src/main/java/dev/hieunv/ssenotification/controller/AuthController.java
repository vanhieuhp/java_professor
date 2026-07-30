package dev.hieunv.ssenotification.controller;

import dev.hieunv.ssenotification.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final JwtService jwtService;

    @PostMapping("/auth/demo-login/{userId}")
    public Map<String, String> demoLogin(@PathVariable String userId) {
        return Map.of("token", jwtService.issue(userId));
    }
}
