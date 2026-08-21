package hieunv.dev.netflixstack.counter.rollup;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

import java.time.Instant;

@Table("counter_rollup")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class RollupCheckpointEntity {

    @PrimaryKey("counter_id")
    private String counterId;

    @Column("last_rollup_count")
    private long lastRollupCount;

    @Column("last_rollup_ts")
    private Instant lastRollupTs;
}
