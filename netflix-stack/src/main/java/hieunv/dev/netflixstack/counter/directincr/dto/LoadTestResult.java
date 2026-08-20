package hieunv.dev.netflixstack.counter.directincr.dto;

import hieunv.dev.netflixstack.counter.dto.ThroughputResult;

public record LoadTestResult(int serverId, ThroughputResult throughput, LatencyStats latency,
                              long finalValue, long expectedValue, boolean exact) {
}
