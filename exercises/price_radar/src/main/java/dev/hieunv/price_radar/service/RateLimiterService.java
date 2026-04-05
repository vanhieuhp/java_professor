package dev.hieunv.price_radar.service;

import java.util.Map;

public interface RateLimiterService {
    boolean isAllowed(String product);

    Map<String, Integer> getCounters();
}
