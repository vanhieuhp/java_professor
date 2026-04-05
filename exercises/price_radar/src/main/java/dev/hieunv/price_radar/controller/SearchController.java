package dev.hieunv.price_radar.controller;

import dev.hieunv.price_radar.model.PriceResult;
import dev.hieunv.price_radar.service.PriceCacheService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SearchController {
    private final PriceCacheService cacheService;
    private final AtomicLong totalRequests = new AtomicLong(0);

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam String product) {
        totalRequests.incrementAndGet();
        List<PriceResult> all = cacheService.getPrices(product);
        PriceResult cheapest = all.stream()
                .min(Comparator.comparingDouble(PriceResult::getPrice))
                .orElseThrow();

        return Map.of(
                "product", product,
                "cheapest", cheapest,
                "all", all
        );
    }

    public long getTotalRequests() {
        return totalRequests.get();
    }
}
