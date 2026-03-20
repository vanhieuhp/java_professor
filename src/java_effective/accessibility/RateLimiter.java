package java_effective.accessibility;

import java.util.HashMap;
import java.util.Map;

/*Problem: Build a RateLimiter class that:

Has a public method boolean allowRequest(String clientId)
Has a private internal cache of request counts
Has a package-private method void reset() for use by a companion cache eviction utility in the same package
Has a protected method protected long getWindowStart() only for subclasses that track time windows
Must NOT expose any internal data structures (Map, List, etc.) to any caller
* */

public class RateLimiter {
    private final Map<String, Integer> requestCounts;  // Hide this completely
    private final int maxRequests;
    private final long windowMs;
    private long windowStart; // also private - subclasses can call getWindowStart() to access this indirectly

    public RateLimiter(int maxRequests, long windowMs) {
        this.maxRequests = maxRequests;
        this.windowMs = windowMs;
        this.requestCounts = new HashMap<>();
        this.windowStart = System.currentTimeMillis();
    }

    public boolean allowRequest(String clientId) {
        long now = System.currentTimeMillis();

        // If the time window has expired, reset everything
        if (now - windowStart > windowMs) {
            reset();
            windowStart = now;
        }

        int count = requestCounts.getOrDefault(clientId, 0);
        if (count >= maxRequests) {
            return false;  // rate limit exceeded
        }

        // Increment — caller is allowed
        requestCounts.put(clientId, count + 1);
        return true;
    }

    // package-private: NO "public" keyword = only classes in the same package can call this
    // The companion CacheEvictionUtility in the same package can call reset()
    // A caller from another package gets a compile error if they try
    void reset() {
        requestCounts.clear();  // wipes counts but never exposes the Map itself
        windowStart = System.currentTimeMillis();
    }

    // protected: subclasses can read windowStart, but nobody else
    // We return a primitive long — NOT the Map, not any internal object
    protected long getWindowStart() {
        return windowStart;  // safe: primitives are copied, not referenced
    }

    /*What you need to do: Complete the implementation, decide what should be private vs package-private vs protected vs public, and add the appropriate access modifiers throughout.

Expected outcome: A caller in another package can only call allowRequest(). Only classes in the same package can call reset(). Only subclasses can call getWindowStart().
    * */
}
