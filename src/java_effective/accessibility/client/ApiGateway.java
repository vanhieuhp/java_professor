package java_effective.accessibility.client;

import java_effective.accessibility.RateLimiter;

public class ApiGateway {
    private final RateLimiter limiter = new RateLimiter(100, 60_000);

    public void handleRequest(String clientId) {
        if (!limiter.allowRequest(clientId)) {   // OK — public
            throw new RuntimeException("Rate limited");
        }
//        limiter.reset();            // COMPILE ERROR — package-private
//        limiter.getWindowStart();   // COMPILE ERROR — protected (not a subclass)
//        limiter.requestCounts;      // COMPILE ERROR — private
    }
}
