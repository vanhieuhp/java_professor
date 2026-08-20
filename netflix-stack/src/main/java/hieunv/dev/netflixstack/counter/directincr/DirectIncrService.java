package hieunv.dev.netflixstack.counter.directincr;

import hieunv.dev.netflixstack.counter.directincr.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.directincr.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.directincr.dto.ValueResponse;
import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;

/**
 * Architecture A - Direct INCR, exposed over HTTP instead of a batch main().
 *
 * Each simulated "server" (serverId 0..serverCount-1) owns its own Redis
 * connection pool, but all servers write to the same shared key - there is
 * no local buffering and no sharding, so every increment() call is a real
 * synchronous round trip to Redis. This makes correctness trivial (INCR is
 * atomic) but throughput is capped by network latency, which the load test
 * makes visible.
 */
public interface DirectIncrService {

    IncrementResponse increment(int serverId);

    ValueResponse currentValue();

    void reset();

    LoadTestResult runLoadTest(int serverId, LoadTestRequest request);
}
