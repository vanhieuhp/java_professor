package hieunv.dev.netflixstack.counter.hybridshardedflush.dto;

public record IncrementResponse(int serverId, String shardKey, long localPendingApprox) {
}
