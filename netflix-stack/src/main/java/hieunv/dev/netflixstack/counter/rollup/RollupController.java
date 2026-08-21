package hieunv.dev.netflixstack.counter.rollup;

import hieunv.dev.netflixstack.counter.rollup.dto.CounterValueResponse;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Manual driver for the rollup pipeline. In production a scheduler would call
 * rollup() on a cadence; here you trigger a pass by hand so the lag between
 * "events written" and "checkpoint moved" is something you can watch:
 *
 * <pre>
 * POST /api/events                        {"counterId":"video:123","eventTime":"...","eventId":"e1","delta":1}
 * GET  /api/counters/rollup/video:123/value        -> rolledUpCount=0, tailEvents=1
 * POST /api/counters/rollup/video:123/rollup       -> eventsAggregated=1
 * GET  /api/counters/rollup/video:123/value        -> rolledUpCount=1, tailEvents=0
 * </pre>
 *
 * <p>Deliberately silent: all logging for this slice lives in
 * {@link RollupServiceImpl}. A pass triggered by a future scheduler would
 * never pass through here, so logging at the controller would only cover the
 * manual path - and would log the same call twice once the service logs it too.
 */
@RestController
@RequestMapping("/api/counters/rollup")
@RequiredArgsConstructor
public class RollupController {

    private final RollupService service;

    @PostMapping("/{counterId}/rollup")
    public RollupResult rollup(@PathVariable String counterId) {
        return service.rollup(counterId);
    }

    @GetMapping("/{counterId}/value")
    public CounterValueResponse value(@PathVariable String counterId) {
        return service.currentValue(counterId);
    }

    @GetMapping("/{counterId}/checkpoint")
    public RollupCheckpoint checkpoint(@PathVariable String counterId) {
        return service.checkpoint(counterId);
    }

    @PutMapping("/{counterId}/checkpoint")
    public RollupCheckpoint overrideCheckpoint(@PathVariable String counterId,
                                               @RequestParam long count,
                                               @RequestParam Instant ts,
                                               @RequestParam(required = false) Long writeTimestampMicros) {
        return service.overrideCheckpoint(counterId, count, ts, writeTimestampMicros);
    }
}
