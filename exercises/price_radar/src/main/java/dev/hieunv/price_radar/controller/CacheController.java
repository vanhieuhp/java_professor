package dev.hieunv.price_radar.controller;

import dev.hieunv.price_radar.service.PriceCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CacheController {

    private final PriceCacheService cacheService;

    @GetMapping("/cache/stats")
    public Map<String, Long> cacheStats() {
        return cacheService.getStats();
    }

}
