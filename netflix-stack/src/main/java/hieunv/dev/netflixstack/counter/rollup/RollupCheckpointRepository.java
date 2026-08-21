package hieunv.dev.netflixstack.counter.rollup;

import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;

import java.time.Instant;

interface RollupCheckpointRepository extends CassandraRepository<RollupCheckpointEntity, String> {

    @Query("INSERT INTO counter_rollup (counter_id, last_rollup_count, last_rollup_ts) "
            + "VALUES (?0, ?1, ?2) USING TIMESTAMP ?3")
    void upsertWithTimestamp(String counterId, long lastRollupCount, Instant lastRollupTs,
                             long writeTimestampMicros);
}
