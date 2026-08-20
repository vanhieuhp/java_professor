package hieunv.dev.netflixstack.counter.directincr.dto;

public record IncrementResponse(int serverId, long value, double latencyMs) {
}
