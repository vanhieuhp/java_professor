package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceResult;

import java.util.List;
import java.util.Map;

public interface PriceAggregatorService {
    List<PriceResult> fetchAllPrices(String product);

    PriceResult findCheapest(String product);

    Map<String, Object> getPoolStats();
}
