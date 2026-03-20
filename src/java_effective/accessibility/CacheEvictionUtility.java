package java_effective.accessibility;

public class CacheEvictionUtility {
    public void evictExpired(RateLimiter limiter) {
        limiter.reset();  // compiles fine — same package
    }
}
