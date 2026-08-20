package hieunv.dev.netflixstack.counter.eventlog;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * See {@link EventLogStore}. Backed by Spring Data Cassandra instead of a
 * hand-built CqlSession: {@link CounterEventEntity}/{@link CounterEventKey}
 * mirror the counter_events table's partition/clustering key exactly, so
 * the same lessons as before still hold - time_bucket is part of the
 * partition key, so a range read spanning more than one hour still means
 * one query per bucket (the loop in readEventsSince), and
 * repository.save() with the full key already set is a plain
 * INSERT/upsert - no separate exists-check needed, matching Cassandra's
 * "same primary key = overwrite" semantics.
 */
@Repository
@RequiredArgsConstructor
public class CassandraEventLogStore implements EventLogStore {

    private static final DateTimeFormatter BUCKET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);

    private final CounterEventRepository repository;

    @Override
    public void addEvent(CounterEvent event) {
        CounterEventKey key = new CounterEventKey(
                event.counterId(), bucketOf(event.eventTime()), event.eventTime(), event.eventId());
        repository.save(new CounterEventEntity(key, event.delta()));
    }

    @Override
    public List<CounterEvent> readEventsSince(String counterId, Instant since) {
        List<CounterEvent> events = new ArrayList<>();
        for (String bucket : bucketsBetween(since, Instant.now())) {
            for (CounterEventEntity entity : repository.findSince(counterId, bucket, since)) {
                events.add(toCounterEvent(entity));
            }
        }
        return events;
    }

    private static CounterEvent toCounterEvent(CounterEventEntity entity) {
        CounterEventKey key = entity.getKey();
        return new CounterEvent(key.getCounterId(), key.getEventTime(), key.getEventId(), entity.getDelta());
    }

    private static String bucketOf(Instant instant) {
        return BUCKET_FORMATTER.format(instant);
    }

    /**
     * time_bucket is part of the partition key, so a range read spanning
     * more than one hour has to loop one partition query per bucket -
     * Cassandra has no efficient way to range-scan across partitions in a
     * single statement. This is the cost of keeping partitions bounded
     * instead of letting one hot counter's events grow one partition
     * without limit.
     */
    private static List<String> bucketsBetween(Instant since, Instant until) {
        List<String> buckets = new ArrayList<>();
        Instant cursor = since.truncatedTo(ChronoUnit.HOURS);
        Instant lastBucketStart = until.truncatedTo(ChronoUnit.HOURS);
        while (!cursor.isAfter(lastBucketStart)) {
            buckets.add(bucketOf(cursor));
            cursor = cursor.plus(1, ChronoUnit.HOURS);
        }
        return buckets;
    }
}
