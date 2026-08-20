package hieunv.dev.netflixstack.counter.eventlog;

import java.time.Instant;

public record CounterEvent(String counterId, Instant eventTime, String eventId, long delta) {
}
