package hieunv.dev.netflixstack.counter.localadderflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.localadderflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.localadderflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.ValueResponse;

/**
 * Architecture B - Local LongAdder + periodic flush, exposed over HTTP.
 *
 * Each simulated "server" (serverId 0..serverCount-1) keeps its own
 * LongAdder in RAM. increment() only touches that adder - it never blocks
 * on Redis, so calling it repeatedly from curl/k6 is a real test of the
 * hot path. A background flusher (one thread per server, started at
 * application startup and running for the app's whole lifetime) drains
 * each adder into the single shared key every flushIntervalMs. Because all
 * servers write the same shared key, don't run two load tests
 * concurrently on different serverIds - they'd race on the same reset.
 */
public interface LocalAdderFlushService {

    IncrementResponse increment(int serverId);

    StatusResponse status(int serverId);

    ValueResponse currentValue();

    LoadTestResult runLoadTest(int serverId, LoadTestRequest request);
}
