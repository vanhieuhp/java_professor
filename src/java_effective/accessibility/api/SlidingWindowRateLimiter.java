package java_effective.accessibility.api;

import java_effective.accessibility.RateLimiter;

public class SlidingWindowRateLimiter extends RateLimiter {

    public SlidingWindowRateLimiter(int max, long windowMs) {
        super(max, windowMs);
    }

    public boolean isWindowExpired() {
        long age = System.currentTimeMillis() - getWindowStart();  // OK — protected
        return age > 5000;
    }

    public void tryEvict() {
//        reset();  // COMPILE ERROR — reset() is package-private, this class is in a different package
    }
}
