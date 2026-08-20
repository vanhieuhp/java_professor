package hieunv.dev.netflixstack.counter.dto;

/**
 * Result of the local-only part of a load test: how fast the request path
 * itself ran, independent of Redis. Comparable across architectures A, B
 * and C since it always measures the same thing - completed operations
 * over wall-clock elapsed time under a synchronized concurrent burst.
 */
public record ThroughputResult(long completedOps, double elapsedSeconds, double throughputOpsPerSec) {
}
