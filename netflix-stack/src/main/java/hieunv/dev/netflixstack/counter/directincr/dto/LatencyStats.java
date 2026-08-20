package hieunv.dev.netflixstack.counter.directincr.dto;

public record LatencyStats(double p50Ms, double p90Ms, double p99Ms, double maxMs) {
}
