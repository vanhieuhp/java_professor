package dev.hieunv.ssenotification.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    @GetMapping(value = "/numbers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNumbers() {
        SseEmitter emitter = new SseEmitter(60_000L);
        registerLifecycleLogs(emitter, "numbers");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .reconnectTime(10000L)
                            .name("number").data("value " + i);
                    emitter.send(event);
                }
                emitter.send(SseEmitter.event().name("done").data("stream finished"));
                emitter.complete();
            } catch (IOException e) {
                emitter.completeWithError(e);
            } finally {
                executor.shutdown(); // bắt buộc: newSingleThreadExecutor tạo non-daemon thread, không shutdown() sẽ treo vĩnh viễn
            }
        });
        return emitter;
    }

    @GetMapping(value = "/clock", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClock() {
        SseEmitter emitter = new SseEmitter(3_000L);
        registerLifecycleLogs(emitter, "clock");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    SseEmitter.SseEventBuilder event = SseEmitter.event().name("number").data("time now: " + LocalDateTime.now());
                    emitter.send(event);

                    Thread.sleep(2000);
                }
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });
        return emitter;
    }

    private void registerLifecycleLogs(SseEmitter emitter, String name) {
        emitter.onCompletion(() -> log.info("[{}] completed", name));
        emitter.onTimeout(() -> log.info("[{}] timeout", name));
        emitter.onError(ex -> log.warn("[{}] error: {}", name, ex.getMessage()));
    }
}
