package hieunv.dev.netflixstack.counter.dto;

/**
 * Parameters for an on-demand load test triggered via the {@code /load-test}
 * endpoints. All fields are optional - each service applies its own
 * defaults when a field is null, so {@code POST .../load-test} with an
 * empty (or absent) body is a valid request.
 */
public record LoadTestRequest(Integer threads, Integer warmupOpsPerThread, Integer opsPerThread) {

    public static final LoadTestRequest DEFAULT = new LoadTestRequest(null, null, null);

    public int threadsOrDefault(int defaultValue) {
        return threads == null ? defaultValue : threads;
    }

    public int warmupOpsPerThreadOrDefault(int defaultValue) {
        return warmupOpsPerThread == null ? defaultValue : warmupOpsPerThread;
    }

    public int opsPerThreadOrDefault(int defaultValue) {
        return opsPerThread == null ? defaultValue : opsPerThread;
    }
}
