package hieunv.dev.netflixstack.counter.directincr;

import hieunv.dev.netflixstack.counter.directincr.dto.IncrementResponse;
import hieunv.dev.netflixstack.counter.directincr.dto.LoadTestResult;
import hieunv.dev.netflixstack.counter.directincr.dto.ValueResponse;
import hieunv.dev.netflixstack.counter.dto.LoadTestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Architecture A - Direct INCR. Every increment is a synchronous Jedis.incr
 * call, so calling this endpoint from curl/Postman one at a time is already
 * a faithful test of the architecture - unlike B and C, there is no local
 * buffering to observe.
 */
@Slf4j
@RestController
@RequestMapping("/api/counters/direct-incr")
public class DirectIncrController {

    private final DirectIncrService service;

    public DirectIncrController(DirectIncrService service) {
        this.service = service;
    }

    @PostMapping("/servers/{serverId}/increment")
    public IncrementResponse increment(@PathVariable int serverId) {
        log.info("POST /direct-incr/servers/{}/increment", serverId);
        IncrementResponse response = service.increment(serverId);
        log.info("POST /direct-incr/servers/{}/increment -> {}", serverId, response);
        return response;
    }

    @GetMapping("/value")
    public ValueResponse value() {
        log.info("GET /direct-incr/value");
        ValueResponse response = service.currentValue();
        log.info("GET /direct-incr/value -> {}", response);
        return response;
    }

    @DeleteMapping("/value")
    public void reset() {
        log.info("DELETE /direct-incr/value");
        service.reset();
    }

    @PostMapping("/servers/{serverId}/load-test")
    public LoadTestResult loadTest(@PathVariable int serverId,
                                    @RequestBody(required = false) LoadTestRequest request) {
        log.info("POST /direct-incr/servers/{}/load-test request={}", serverId, request);
        LoadTestResult result = service.runLoadTest(serverId, request == null ? LoadTestRequest.DEFAULT : request);
        log.info("POST /direct-incr/servers/{}/load-test -> {}", serverId, result);
        return result;
    }
}
