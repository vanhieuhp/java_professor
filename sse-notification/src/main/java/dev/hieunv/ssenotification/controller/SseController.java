package dev.hieunv.ssenotification.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/sse")
public class SseController {

    private static final Logger log = LoggerFactory.getLogger(SseController.class);

    @GetMapping(value = "/numbers", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNumbers() {
        log.info("[numbers] streamNumbers called on thread: {}", Thread.currentThread().getName());

        SseEmitter emitter = new SseEmitter(60_000L);
        registerLifecycleLogs(emitter, "numbers");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                for (int i = 1; i <= 10; i++) {
                    SseEmitter.SseEventBuilder event = SseEmitter.event()
                            .reconnectTime(10000L)
                            .name("number").data("value " + i);
                    log.info("[numbers] sending event on thread: {}", Thread.currentThread().getName());
                    emitter.send(event);
                    Thread.sleep(500);
                }
                emitter.send(SseEmitter.event().name("done").data("stream finished"));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            } finally {
                executor.shutdown(); // bắt buộc: newSingleThreadExecutor tạo non-daemon thread, không shutdown() sẽ treo vĩnh viễn
            }
        });

        emitter.onCompletion(() -> {
        });

        return emitter;
    }

    @GetMapping(value = "/clock", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClock() {
        log.info("[clock] streamClock called on thread: {}", Thread.currentThread().getName());

        SseEmitter emitter = new SseEmitter(3_000L);
        registerLifecycleLogs(emitter, "clock");

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    SseEmitter.SseEventBuilder event = SseEmitter.event().name("number").data("time now: " + LocalDateTime.now());
                    log.info("[clock] sending event on thread: {}", Thread.currentThread().getName());
                    emitter.send(event);

                    Thread.sleep(2000);
                }
                emitter.complete();
            } catch (Exception e) {
                log.warn("[clock] send() threw on thread: {} | exception type: {} | message: {}",
                        Thread.currentThread().getName(), e.getClass().getName(), e.getMessage());
                emitter.completeWithError(e);
            } finally {
                executor.shutdown();
            }
        });

        emitter.onCompletion(() -> {
        });

        return emitter;
    }

    private void registerLifecycleLogs(SseEmitter emitter, String name) {
        emitter.onCompletion(() -> log.info("[{}] onCompletion on thread: {}", name, Thread.currentThread().getName()));
        emitter.onTimeout(() -> log.info("[{}] timeout on thread: {}", name, Thread.currentThread().getName()));
        emitter.onError(ex -> log.warn("[{}] onError on thread: {} | exception type: {} | message: {}",
                name, Thread.currentThread().getName(), ex.getClass().getName(), ex.getMessage()));
    }
}
