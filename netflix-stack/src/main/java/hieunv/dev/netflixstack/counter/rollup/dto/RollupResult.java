package hieunv.dev.netflixstack.counter.rollup.dto;

import java.time.Instant;

/**
 * What one rollup pass did, in enough detail to see the pipeline working:
 * how far the high-water mark moved, how many events it folded in, and which
 * write timestamp it used to claim the checkpoint.
 *
 * <p>The three event counters are the proof that the pass is incremental
 * rather than a full re-scan, and they add up:
 * {@code eventsRead = eventsAggregated + eventsSkippedAlreadyRolled + eventsSkippedTooFresh}.
 *
 * @param eventsRead                 rows the event-log read actually returned - on a second
 *                                   pass this covers only the tail past the previous mark,
 *                                   never the counter's whole history
 * @param eventsAggregated           events folded in this pass (0 means the pass was a no-op
 *                                   and no write was issued)
 * @param eventsSkippedAlreadyRolled events sitting exactly on the previous high-water mark,
 *                                   returned by the inclusive read and filtered back out
 * @param eventsSkippedTooFresh      events inside the lag window, deliberately left for the next pass
 */
public record RollupResult(String counterId,
                           long previousCount,
                           long newCount,
                           long delta,
                           int eventsRead,
                           int eventsAggregated,
                           int eventsSkippedAlreadyRolled,
                           int eventsSkippedTooFresh,
                           Instant previousRollupTs,
                           Instant newRollupTs,
                           long writeTimestampMicros,
                           boolean checkpointWritten) {
}
