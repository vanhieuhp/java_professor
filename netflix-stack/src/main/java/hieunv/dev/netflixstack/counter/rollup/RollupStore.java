package hieunv.dev.netflixstack.counter.rollup;

import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;

public interface RollupStore {

    RollupCheckpoint getCheckpoint(String counterId);

    void saveCheckpoint(RollupCheckpoint checkpoint, long writeTimestampMicros);
}
