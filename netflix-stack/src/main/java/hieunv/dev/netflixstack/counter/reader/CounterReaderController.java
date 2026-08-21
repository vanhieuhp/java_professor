package hieunv.dev.netflixstack.counter.reader;

import hieunv.dev.netflixstack.counter.reader.dto.ReadResponse;
import hieunv.dev.netflixstack.counter.reader.dto.ReaderBenchResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two read strategies side by side, on the same counter:
 *
 * <pre>
 * GET  /api/counters/reader/bench-1?mode=accurate        -> checkpoint + tail scan
 * GET  /api/counters/reader/bench-1?mode=eventual        -> whatever the cache/checkpoint says
 * POST /api/counters/reader/bench-1/bench?mode=...&iterations=100
 * </pre>
 *
 * <p>{@code mode} is the reader's bean name, so adding a third strategy means
 * adding a {@code @Service("...")} and nothing here.
 *
 * <p>Logging lives in {@link ReaderBenchmarkService}: a burst of 100 reads
 * logged per request would drown the one line that matters.
 */
@RestController
@RequestMapping("/api/counters/reader")
@RequiredArgsConstructor
public class CounterReaderController {

    private final ReaderBenchmarkService service;

    @GetMapping("/{counterId}")
    public ReadResponse read(@PathVariable String counterId,
                             @RequestParam(defaultValue = "accurate") String mode) {
        return service.read(mode, counterId);
    }

    @PostMapping("/{counterId}/bench")
    public ReaderBenchResult bench(@PathVariable String counterId,
                                   @RequestParam(defaultValue = "accurate") String mode,
                                   @RequestParam(defaultValue = "100") int iterations,
                                   @RequestParam(defaultValue = "5") int warmup) {
        return service.bench(mode, counterId, iterations, warmup);
    }
}
