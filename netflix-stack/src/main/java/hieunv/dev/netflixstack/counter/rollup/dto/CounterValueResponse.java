package hieunv.dev.netflixstack.counter.rollup.dto;

import java.time.Instant;

public record CounterValueResponse(String counterId,
                                   long value,
                                   long rolledUpCount,
                                   long tailDelta,
                                   int tailEvents,
                                   Instant lastRollupTs) {
}
