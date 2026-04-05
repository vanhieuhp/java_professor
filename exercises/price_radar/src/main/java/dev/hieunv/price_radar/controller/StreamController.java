package dev.hieunv.price_radar.controller;

import dev.hieunv.price_radar.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StreamController {

    private final NotificationService notificationService;

    @GetMapping("/stream/{userId}")
    public SseEmitter stream(@PathVariable String userId) {
        return notificationService.subscribe(userId);
    }

}
