package hieunv.dev.netflixstack.counter.localadderflush.dto;

/**
 * localPendingApprox is a non-destructive read (LongAdder.sum(), not
 * sumThenReset()) of what has accumulated locally since the last flush -
 * useful for watching a value build up between flush ticks.
 */
public record StatusResponse(int serverId, long localPendingApprox) {
}
