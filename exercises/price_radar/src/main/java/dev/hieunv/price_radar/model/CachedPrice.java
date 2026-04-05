package dev.hieunv.price_radar.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CachedPrice {

    private List<PriceResult> prices;
    Instant cachedAt;
}
