package hieunv.dev.netflixstack.counter.eventlog;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.time.Instant;
import java.util.List;

interface CounterEventRepository extends CassandraRepository<CounterEventEntity, CounterEventKey> {

    @Query("SELECT * FROM counter_events WHERE counter_id = ?0 AND time_bucket = ?1 AND event_time >= ?2")
    List<CounterEventEntity> findSince(String counterId, String timeBucket, Instant since);
}
