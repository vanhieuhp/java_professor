package hieunv.dev.netflixstack.counter.hybridshardedflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ChaosSlowRequest;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.hybridshardedflush.dto.ValueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture C - Hybrid Sharded Flush. A typical interactive session:
 * POST increment a few times, GET status to watch pendingRetry build up,
 * POST chaos/dead to kill that server's Redis path, watch status flip the
 * circuit breaker to OPEN and dropped start climbing, POST chaos/recover,
 * watch it go back to CLOSED.
 */
@Slf4j
@RestController
@RequestMapping("/api/counters/hybrid-sharded-flush")
@RequiredArgsConstructor
public class HybridShardedFlushController {

    private final HybridShardedFlushService service;

    @PostMapping("/servers/{serverId}/increment")
    public IncrementResponse increment(@PathVariable int serverId) {
        log.info("POST /hybrid-sharded-flush/servers/{}/increment", serverId);
        IncrementResponse response = service.increment(serverId);
        log.info("POST /hybrid-sharded-flush/servers/{}/increment -> {}", serverId, response);
        return response;
    }

    @GetMapping("/servers/{serverId}/status")
    public StatusResponse status(@PathVariable int serverId) {
        log.info("GET /hybrid-sharded-flush/servers/{}/status", serverId);
        StatusResponse response = service.status(serverId);
        log.info("GET /hybrid-sharded-flush/servers/{}/status -> {}", serverId, response);
        return response;
    }

    @GetMapping("/value")
    public ValueResponse value() {
        log.info("GET /hybrid-sharded-flush/value");
        ValueResponse response = service.currentValue();
        log.info("GET /hybrid-sharded-flush/value -> {}", response);
        return response;
    }

    @PostMapping("/servers/{serverId}/chaos/dead")
    public void induceDeadChaos(@PathVariable int serverId) {
        log.warn("POST /hybrid-sharded-flush/servers/{}/chaos/dead - inducing Redis outage", serverId);
        service.induceDeadChaos(serverId);
    }

    @PostMapping("/servers/{serverId}/chaos/slow")
    public void induceSlowChaos(@PathVariable int serverId, @RequestBody ChaosSlowRequest request) {
        log.warn("POST /hybrid-sharded-flush/servers/{}/chaos/slow request={}", serverId, request);
        service.induceSlowChaos(serverId, request);
    }

    @PostMapping("/servers/{serverId}/chaos/recover")
    public void recoverChaos(@PathVariable int serverId) {
        log.info("POST /hybrid-sharded-flush/servers/{}/chaos/recover", serverId);
        service.recoverChaos(serverId);
    }

    @PostMapping("/servers/{serverId}/load-test")
    public LoadTestResult loadTest(@PathVariable int serverId,
                                   @RequestBody(required = false) LoadTestRequest request) {
        log.info("POST /hybrid-sharded-flush/servers/{}/load-test request={}", serverId, request);
        LoadTestResult result = service.runLoadTest(serverId, request == null ? LoadTestRequest.DEFAULT : request);
        log.info("POST /hybrid-sharded-flush/servers/{}/load-test -> {}", serverId, result);
        return result;
    }
}
