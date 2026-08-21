package hieunv.dev.netflixstack.counter.rollup.dto;

import java.time.Instant;

public record RollupCheckpoint(String counterId, long lastRollupCount, Instant lastRollupTs) {

    public static RollupCheckpoint empty(String counterId) {
        return new RollupCheckpoint(counterId, 0L, Instant.EPOCH);
    }
}
