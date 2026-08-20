package hieunv.dev.netflixstack.counter.localadderflush;

import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import hieunv.dev.netflixstack.counter.localadderflush.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.localadderflush.dto.StatusResponse;
import hieunv.dev.netflixstack.counter.localadderflush.dto.ValueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture B - Local LongAdder + periodic flush. increment() returns
 * immediately (no Redis call); call GET .../value repeatedly (or watch
 * status) to see the flush lag for yourself instead of relying on the old
 * benchmark's internal probe.
 */
@Slf4j
@RestController
@RequestMapping("/api/counters/local-adder-flush")
public class LocalAdderFlushController {

    private final LocalAdderFlushService service;

    public LocalAdderFlushController(LocalAdderFlushService service) {
        this.service = service;
    }

    @PostMapping("/servers/{serverId}/increment")
    public IncrementResponse increment(@PathVariable int serverId) {
        log.info("POST /local-adder-flush/servers/{}/increment", serverId);
        IncrementResponse response = service.increment(serverId);
        log.info("POST /local-adder-flush/servers/{}/increment -> {}", serverId, response);
        return response;
    }

    @GetMapping("/servers/{serverId}/status")
    public StatusResponse status(@PathVariable int serverId) {
        log.info("GET /local-adder-flush/servers/{}/status", serverId);
        StatusResponse response = service.status(serverId);
        log.info("GET /local-adder-flush/servers/{}/status -> {}", serverId, response);
        return response;
    }

    @GetMapping("/value")
    public ValueResponse value() {
        log.info("GET /local-adder-flush/value");
        ValueResponse response = service.currentValue();
        log.info("GET /local-adder-flush/value -> {}", response);
        return response;
    }

    @PostMapping("/servers/{serverId}/load-test")
    public LoadTestResult loadTest(@PathVariable int serverId,
                                    @RequestBody(required = false) LoadTestRequest request) {
        log.info("POST /local-adder-flush/servers/{}/load-test request={}", serverId, request);
        LoadTestResult result = service.runLoadTest(serverId, request == null ? LoadTestRequest.DEFAULT : request);
        log.info("POST /local-adder-flush/servers/{}/load-test -> {}", serverId, result);
        return result;
    }
}
