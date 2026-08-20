package hieunv.dev.netflixstack.counter.eventlog;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

import java.io.Serializable;
import java.time.Instant;

/**
 * Mirrors counter_events' composite primary key exactly:
 * PRIMARY KEY ((counter_id, time_bucket), event_time, event_id).
 * counterId + timeBucket partition the data (time_bucket keeps a single
 * counter's partition bounded to one hour); eventTime + eventId cluster
 * rows within a partition, and together are the idempotency token - the
 * same (counterId, timeBucket, eventTime, eventId) always overwrites the
 * same row instead of creating a duplicate.
 */
@PrimaryKeyClass
@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class CounterEventKey implements Serializable {

    @PrimaryKeyColumn(name = "counter_id", ordinal = 0, type = PrimaryKeyType.PARTITIONED)
    private String counterId;

    @PrimaryKeyColumn(name = "time_bucket", ordinal = 1, type = PrimaryKeyType.PARTITIONED)
    private String timeBucket;

    @PrimaryKeyColumn(name = "event_time", ordinal = 2, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private Instant eventTime;

    @PrimaryKeyColumn(name = "event_id", ordinal = 3, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.ASCENDING)
    private String eventId;
}
