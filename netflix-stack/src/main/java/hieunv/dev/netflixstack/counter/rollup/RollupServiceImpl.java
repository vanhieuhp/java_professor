package hieunv.dev.netflixstack.counter.rollup;

import hieunv.dev.netflixstack.counter.eventlog.CounterEvent;
import hieunv.dev.netflixstack.counter.eventlog.EventLogStore;
import hieunv.dev.netflixstack.counter.rollup.dto.CounterValueResponse;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RollupServiceImpl implements RollupService {

    private final EventLogStore eventLogStore;
    private final RollupStore rollupStore;

    @Value("${netflix-stack.counter.rollup.lag-millis:1000}")
    private long rollupLagMillis;

    @Value("${netflix-stack.counter.rollup.max-lookback-hours:24}")
    private long maxLookbackHours;

    @Override
    public RollupResult rollup(String counterId) {
        requireCounterId(counterId);

        Instant passStartedAt = Instant.now();
        Instant safeUpperBound = passStartedAt.minusMillis(rollupLagMillis);

        RollupCheckpoint checkpoint = rollupStore.getCheckpoint(counterId);
        Instant previousTs = checkpoint.lastRollupTs();
        log.debug("rollup[{}] pass started at={} checkpoint(count={}, ts={}) lagMillis={} safeUpperBound={}",
                counterId, passStartedAt, checkpoint.lastRollupCount(), previousTs, rollupLagMillis, safeUpperBound);

        Instant readFrom = readFloor(counterId, passStartedAt, previousTs);
        List<CounterEvent> events = eventLogStore.readEventsSince(counterId, readFrom);
        log.debug("rollup[{}] read {} candidate event(s) since {}", counterId, events.size(), readFrom);

        long delta = 0L;
        int aggregated = 0;
        int skippedTooFresh = 0;
        int skippedAlreadyRolled = 0;
        Instant newTs = previousTs;

        for (CounterEvent event : events) {
            if (!event.eventTime().isAfter(previousTs)) {
                skippedAlreadyRolled++; // already folded into lastRollupCount by an earlier pass
                continue;
            }
            if (event.eventTime().isAfter(safeUpperBound)) {
                skippedTooFresh++; // inside the lag window - next pass will take it
                continue;
            }
            delta += event.delta();
            aggregated++;
            if (event.eventTime().isAfter(newTs)) {
                newTs = event.eventTime();
            }
        }

        if (skippedAlreadyRolled > 0) {
            log.debug("rollup[{}] skipped {} event(s) already folded in at or before {}",
                    counterId, skippedAlreadyRolled, previousTs);
        }

        long newCount = checkpoint.lastRollupCount() + delta;
        boolean written = aggregated > 0;
        long lastTimeRollup = toMicros(newTs);
        if (written) {
            rollupStore.saveCheckpoint(new RollupCheckpoint(counterId, newCount, newTs), lastTimeRollup);
            log.info("rollup[{}] folded {} event(s): count {} -> {} (delta={}), ts {} -> {}, "
                            + "usingTimestamp={}, {} event(s) left inside the lag window",
                    counterId, aggregated, checkpoint.lastRollupCount(), newCount, delta,
                    previousTs, newTs, lastTimeRollup, skippedTooFresh);
        } else {
            log.debug("rollup[{}] no-op: nothing to fold in ({} event(s) still inside the lag window), "
                            + "checkpoint left at count={} ts={}",
                    counterId, skippedTooFresh, checkpoint.lastRollupCount(), previousTs);
        }

        return new RollupResult(counterId, checkpoint.lastRollupCount(), newCount, delta,
                events.size(), aggregated, skippedAlreadyRolled, skippedTooFresh,
                previousTs, newTs, lastTimeRollup, written);
    }

    @Override
    public CounterValueResponse currentValue(String counterId) {
        requireCounterId(counterId);

        RollupCheckpoint checkpoint = rollupStore.getCheckpoint(counterId);
        Instant since = checkpoint.lastRollupTs();
        Instant readFrom = readFloor(counterId, Instant.now(), since);

        long tailDelta = 0L;
        int tailEvents = 0;
        for (CounterEvent event : eventLogStore.readEventsSince(counterId, readFrom)) {
            if (!event.eventTime().isAfter(since)) {
                continue;
            }
            tailDelta += event.delta();
            tailEvents++;
        }

        long value = checkpoint.lastRollupCount() + tailDelta;
        log.debug("rollup[{}] read value={} (rolledUp={} + tail={} over {} event(s) newer than {})",
                counterId, value, checkpoint.lastRollupCount(), tailDelta, tailEvents, since);

        return new CounterValueResponse(counterId, value, checkpoint.lastRollupCount(),
                tailDelta, tailEvents, since);
    }

    @Override
    public RollupCheckpoint checkpoint(String counterId) {
        requireCounterId(counterId);
        RollupCheckpoint checkpoint = rollupStore.getCheckpoint(counterId);
        log.debug("rollup[{}] checkpoint read: count={} ts={}",
                counterId, checkpoint.lastRollupCount(), checkpoint.lastRollupTs());
        return checkpoint;
    }

    @Override
    public RollupCheckpoint overrideCheckpoint(String counterId, long count, Instant ts, Long writeTimestampMicros) {
        requireCounterId(counterId);

        long micros = writeTimestampMicros == null ? toMicros(Instant.now()) : writeTimestampMicros;
        RollupCheckpoint before = rollupStore.getCheckpoint(counterId);
        rollupStore.saveCheckpoint(new RollupCheckpoint(counterId, count, ts), micros);

        RollupCheckpoint after = rollupStore.getCheckpoint(counterId);
        if (after.lastRollupCount() == count && ts.equals(after.lastRollupTs())) {
            log.info("rollup[{}] checkpoint overridden: count {} -> {}, ts {} -> {}, usingTimestamp={}",
                    counterId, before.lastRollupCount(), count, before.lastRollupTs(), ts, micros);
        } else {
            log.warn("rollup[{}] checkpoint override DISCARDED - usingTimestamp={} lost to a newer write; "
                            + "requested count={} ts={}, stored count={} ts={}",
                    counterId, micros, count, ts, after.lastRollupCount(), after.lastRollupTs());
        }
        return after;
    }

    private Instant readFloor(String counterId, Instant passStartedAt, Instant lastRollupTs) {
        Instant floor = passStartedAt.minus(maxLookbackHours, ChronoUnit.HOURS);
        if (!lastRollupTs.isBefore(floor)) {
            return lastRollupTs;
        }
        if (Instant.EPOCH.equals(lastRollupTs)) {
            log.debug("rollup[{}] first pass - reading from the {}h lookback floor {} instead of EPOCH",
                    counterId, maxLookbackHours, floor);
        } else {
            log.warn("rollup[{}] checkpoint ts={} is older than the {}h lookback floor {} - "
                            + "events in between will never be folded in",
                    counterId, lastRollupTs, maxLookbackHours, floor);
        }
        return floor;
    }

    private static long toMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private static void requireCounterId(String counterId) {
        if (counterId == null || counterId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "counterId must not be blank");
        }
    }
}
