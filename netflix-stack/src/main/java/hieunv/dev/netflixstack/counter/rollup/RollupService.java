package hieunv.dev.netflixstack.counter.rollup;

import hieunv.dev.netflixstack.counter.rollup.dto.CounterValueResponse;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupResult;

import java.time.Instant;

public interface RollupService {

    RollupResult rollup(String counterId);

    CounterValueResponse currentValue(String counterId);

    RollupCheckpoint checkpoint(String counterId);

    RollupCheckpoint overrideCheckpoint(String counterId, long count, Instant ts, Long writeTimestampMicros);
}
