package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceResult;

import java.util.List;
import java.util.Map;

public interface PriceCacheService {
    List<PriceResult> getPrices(String product);

    List<PriceResult> getPricesV2(String product);

    Map<String, Long> getStats();
}
