package dev.hieunv.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/echo")
public class EchoController {

    public record EchoDto(String message, int times) {
    }

    @PostMapping
    public Map<String, Object> echo(@RequestBody EchoDto echoDto) {
        return Map.of("ok", true,
                "received", echoDto);
    }
}
