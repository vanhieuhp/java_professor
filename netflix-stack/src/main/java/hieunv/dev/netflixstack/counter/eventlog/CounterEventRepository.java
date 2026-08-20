package hieunv.dev.netflixstack.counter.eventlog;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.time.Instant;
import java.util.List;

/**
 * Package-private: an implementation detail of {@link CassandraEventLogStore},
 * not meant to be used directly elsewhere.
 *
 * findSince() is one partition query (counter_id + time_bucket fix the
 * partition, event_time filters within it) - readEventsSince() in the
 * store calls this once per hour bucket to cover a wider range.
 */
interface CounterEventRepository extends CassandraRepository<CounterEventEntity, CounterEventKey> {

    @Query("SELECT * FROM counter_events WHERE counter_id = ?0 AND time_bucket = ?1 AND event_time >= ?2")
    List<CounterEventEntity> findSince(String counterId, String timeBucket, Instant since);
}
