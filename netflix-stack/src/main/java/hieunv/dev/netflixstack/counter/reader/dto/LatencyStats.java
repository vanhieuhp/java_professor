package hieunv.dev.netflixstack.counter.reader.dto;

public record LatencyStats(double minMs, double p50Ms, double p90Ms, double p99Ms, double maxMs, double avgMs) {
}
