package dev.hieunv.price_radar.controller;

import dev.hieunv.price_radar.service.AlertService;
import dev.hieunv.price_radar.service.NotificationService;
import dev.hieunv.price_radar.service.PriceAggregatorService;
import dev.hieunv.price_radar.service.PriceCacheService;
import dev.hieunv.price_radar.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class StatsController {

    private final PriceCacheService cacheService;
    private final AlertService alertService;
    private final NotificationService notificationService;
    private final PriceAggregatorService aggregatorService;
    private final RateLimiterService rateLimiterService;
    private final SearchController searchController;

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        int totalAlerts = alertService.getAllAlerts()
                .values().stream()
                .mapToInt(List::size)
                .sum();

        return Map.of(
            "cache",          cacheService.getStats(),
            "threadPool",     aggregatorService.getPoolStats(),
            "activeAlerts",   totalAlerts,
            "sseConnections", notificationService.activeConnections(),
            "totalRequests",  searchController.getTotalRequests(),
            "rateLimiter",    rateLimiterService.getCounters()
        );
    }
}
