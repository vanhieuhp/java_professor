package hieunv.dev.netflixstack.counter.hybridshardedflush.dto;

import hieunv.dev.netflixstack.counter.dto.ThroughputResult;

public record LoadTestResult(int serverId, String shardKey, ThroughputResult throughput,
                              long finalValue, long expectedValue, boolean exact) {
}
