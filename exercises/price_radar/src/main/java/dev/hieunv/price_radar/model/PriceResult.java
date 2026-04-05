package dev.hieunv.price_radar.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PriceResult {

    private String supplierName;
    private String product;
    private double price;
    private long responseTimeMs;
}
