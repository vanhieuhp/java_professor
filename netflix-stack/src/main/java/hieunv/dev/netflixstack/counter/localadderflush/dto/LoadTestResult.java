package hieunv.dev.netflixstack.counter.localadderflush.dto;

import hieunv.dev.netflixstack.counter.dto.ThroughputResult;

public record LoadTestResult(int serverId, ThroughputResult throughput,
                              long finalValue, long expectedValue, boolean exact) {
}
