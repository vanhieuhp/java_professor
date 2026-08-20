package hieunv.dev.netflixstack.counter.eventlog;

import java.time.Instant;
import java.util.List;

/**
 * Append-only event log for counter deltas, backed by Cassandra's
 * counter_events table (see docs/docs.md - this is the storage layer of
 * Netflix's Distributed Counter Abstraction pattern: raw events durable in
 * Cassandra, with a rollup cache in front for fast reads).
 */
public interface EventLogStore {

    void addEvent(CounterEvent event);

    List<CounterEvent> readEventsSince(String counterId, Instant since);
}
