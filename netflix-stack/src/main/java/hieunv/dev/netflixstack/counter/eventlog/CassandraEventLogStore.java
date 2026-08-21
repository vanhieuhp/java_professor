package hieunv.dev.netflixstack.counter.eventlog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Repository
@RequiredArgsConstructor
@Slf4j
public class CassandraEventLogStore implements EventLogStore {

    private static final DateTimeFormatter BUCKET_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH").withZone(ZoneOffset.UTC);

    private final CounterEventRepository counterEventRepository;

    @Override
    public void addEvent(CounterEvent event) {
        CounterEventKey key = new CounterEventKey(
                event.counterId(), bucketOf(event.eventTime()), event.eventTime(), event.eventId());
        counterEventRepository.save(new CounterEventEntity(key, event.delta()));
        log.info("Saved event {}", event);
    }

    @Override
    public List<CounterEvent> readEventsSince(String counterId, Instant since) {
        List<CounterEvent> events = new ArrayList<>();
        for (String bucket : bucketsBetween(since, Instant.now())) {
            for (CounterEventEntity entity : counterEventRepository.findSince(counterId, bucket, since)) {
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
