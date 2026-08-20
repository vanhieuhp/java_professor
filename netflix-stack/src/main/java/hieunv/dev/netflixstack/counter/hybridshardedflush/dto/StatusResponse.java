package hieunv.dev.netflixstack.counter.hybridshardedflush.dto;

public record StatusResponse(int serverId, String shardKey, String circuitBreakerState,
                              long pendingRetry, long dropped, long localPendingApprox, long shardValue) {
}
