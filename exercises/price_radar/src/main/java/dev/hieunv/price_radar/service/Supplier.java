package dev.hieunv.price_radar.service;

import dev.hieunv.price_radar.model.PriceResult;

public interface Supplier {
    PriceResult getPrice(String productName);
}
