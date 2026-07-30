package dev.hieunv.ssenotification.controller;

import dev.hieunv.ssenotification.service.SseEmitterRegistry;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final SseEmitterRegistry registry;

    @GetMapping(value = "/sse/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(Authentication authentication,
                                 @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId,
                                 HttpServletResponse response) {
        String userId = authentication.getName();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        boolean accepted = registry.subscribe(userId, emitter, lastEventId);
        if (!accepted) {
            log.warn("[{}] subscribe rejected by controller: connection limit reached", userId);
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many concurrent connections for this user");
        }

        response.setHeader("X-Accel-Buffering", "no");
        return emitter;
    }

    @PostMapping("/notify/{userId}")
    public void notify(@PathVariable String userId, @RequestBody String message) {
        registry.push(userId, "notification", message);
    }

    @PostMapping("/debug/disconnect/{userId}")
    public void forceDisconnect(@PathVariable String userId) {
        registry.forceDisconnect(userId);
    }
}
