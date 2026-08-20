package hieunv.dev.netflixstack.counter.eventlog;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("counter_events")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CounterEventEntity {

    @PrimaryKey
    private CounterEventKey key;

    @Column("delta")
    private long delta;
}
