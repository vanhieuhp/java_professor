package hieunv.dev.netflixstack.counter.eventlog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/**
 * Thin REST wrapper over {@link EventLogStore} - lets you poke the
 * Cassandra event log directly (add one event, read events since a point
 * in time) without going through any of the counter architectures A/B/C.
 */
@Slf4j
@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventLogController {

    private final EventLogStore store;

    @PostMapping
    public CounterEvent addEvent(@RequestBody CounterEvent event) {
        log.info("POST /events {}", event);
        store.addEvent(event);
        return event;
    }

    @GetMapping
    public List<CounterEvent> readEventsSince(@RequestParam String counterId, @RequestParam Instant since) {
        log.info("GET /events?counterId={}&since={}", counterId, since);
        List<CounterEvent> events = store.readEventsSince(counterId, since);
        log.info("GET /events?counterId={}&since={} -> {} event(s)", counterId, since, events.size());
        return events;
    }
}
